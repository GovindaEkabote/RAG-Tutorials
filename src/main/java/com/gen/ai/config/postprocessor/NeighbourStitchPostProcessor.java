package com.gen.ai.config.postprocessor;


import com.gen.ai.config.data.RagConfigData;
import com.gen.ai.config.data.RagConstant;
import com.gen.ai.config.postprocessor.record.Target;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.print.Doc;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NeighbourStitchPostProcessor implements DocumentPostProcessor {

    private final VectorStore vectorStore;
    private final RagConfigData ragConfigData;

    public NeighbourStitchPostProcessor(
            @Qualifier("ragVectorStore") VectorStore vectorStore, RagConfigData ragConfigData) {
        this.vectorStore = vectorStore;
        this.ragConfigData = ragConfigData;
    }


    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents.isEmpty()){
            return documents;
        }
        Set<Target> targets= getTargetDocuments(documents);
        List<Document> neighbours = getNeighboursDocuments(targets);
        List<Document> merged = getMergedDocuments(documents, neighbours);
        return dedupBySocuceIndexPreserveOrder(merged);
    }

    /**
     * Extract source and source index from retrieved documents.
     */
    private Set<Target> getTargetDocuments(List<Document> documents) {

        return documents.stream()
                .map(document -> {
                    Map<String, Object> metadata = document.getMetadata();
                    Object source = metadata.get(RagConstant.SOURCE);
                    Object sourceIndex = metadata.get(RagConstant.CHUNK_INDEX);
                    if (sourceIndex == null || source == null){
                        return null;
                    }
                    return new Target(
                            source.toString(),
                            Integer.parseInt(sourceIndex.toString())
                    );
                }).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    /**
     * Fetch neighbouring chunks from VectorStore.
     * This example assumes chunks have metadata:
     * source, sourceIndex.
     */
    private List<Document> getNeighboursDocuments(Set<Target> targets) {
        List<Document> neighbours= new ArrayList<>();

        for (Target target : targets){
            int neighbourWindow = ragConfigData.getRadius();
            int startIndex = Math.max(
                    0,
                    target.index() - neighbourWindow
            );
            int endIndex = target.index() + neighbourWindow;

            SearchRequest searchRequest = SearchRequest.builder()
                    .query("")
                    .topK(ragConfigData.getTopK())
                    .filterExpression(
                            "source == '" + escapeFilter(target.source())
                                    + "' && sourceIndex >= " + startIndex
                                    + " && sourceIndex <= " + endIndex
                    )
                    .build();
            neighbours.addAll(vectorStore.similaritySearch(searchRequest));
        }
            return neighbours;
    }

    /**
     * Merge original retrieved documents with neighbouring documents.
     */
    private List<Document> getMergedDocuments(
            List<Document> documents,
            List<Document> neighbours) {
        Map<String, Document> merged = new LinkedHashMap<>();

        for (Document document : documents) {
            merged.put(getDocumentKey(document), document);
        }

        for (Document neighbour : neighbours) {
            merged.putIfAbsent(
                    getDocumentKey(neighbour),
                    neighbour
            );
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Deduplicate by source and source index,
     * preserving the first occurrence.
     */
    private List<Document> dedupBySocuceIndexPreserveOrder(
            List<Document> documents) {

        Set<String> seen = new HashSet<>();

        return documents.stream()
                .filter(document ->
                        seen.add(getDocumentKey(document)))
                .toList();
    }

    /**
     * Unique document key.
     */
    private String getDocumentKey(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        Object source = metadata.get(RagConstant.SOURCE);
        Object sourceIndex = metadata.get(RagConstant.CHUNK_INDEX);

        if (source == null || sourceIndex == null) {
            return document.getId();
        }

        return source + "::" + sourceIndex;
    }

    private String escapeFilter(String value) {
        return value.replace("\\", "\\\\")
                .replace("'", "\\'");
    }
}
