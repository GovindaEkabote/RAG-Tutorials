package com.gen.ai.service;

import com.gen.ai.config.PgVectorConfigData;
import com.gen.ai.config.data.RagConfigData;
import com.gen.ai.config.data.RagConstant;
import com.gen.ai.exception.RagException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Component
public class RagIngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final RagConfigData ragConfigData;
    private final PdfDocumentReaderConfig pdfDocumentReaderConfig;
    private final TokenTextSplitter tokenTextSplitter;
    private final String ragVectorStoreTableName;

    public RagIngestionService(
            @Qualifier("ragVectorStore") VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            RagConfigData ragConfigData,

            TokenTextSplitter tokenTextSplitter,
            PgVectorConfigData pgVectorConfigData
    ) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.ragConfigData = ragConfigData;
        RagConfigData.PdfProperties pdfProperties = ragConfigData.getPdfProperties();
        this.pdfDocumentReaderConfig = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(pdfProperties.getPagesPerDocument())
                .withPageExtractedTextFormatter(
                        ExtractedTextFormatter.builder()
                                .withLeftAlignment(pdfProperties.isLeftAlignment())
                                .withNumberOfTopPagesToSkipBeforeDelete(pdfProperties.getNumberOfTopTextLinesToDelete())
                                .withNumberOfBottomTextLinesToDelete(pdfProperties.getNumberOfBottomTextLinesToDelete())
                                .build()
                )
                .build();
        this.tokenTextSplitter = tokenTextSplitter;
        this.ragVectorStoreTableName = pgVectorConfigData.getTableNameForRag();
    }

    public void initializePgVectorStore() throws RagException{
        if (skipIngest(jdbcTemplate)){
            return;
        }

        var pdfResources= getResources();
        if (pdfResources == null){
            return;
        }
        ingestDocumentChunksToVectorStore(pdfResources);
    }

    private Resource[] getResources(){
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            String pdfPath = ragConfigData.getPdfProperties().getPath();
            Resource[] pdfResources= resolver.getResources(pdfPath);
            if (pdfResources.length == 0){
                log.warn("No PDF Found");
                return null;
            }
            return pdfResources;
        } catch (Exception e) {
            throw new RagException("Could not get pdf resources! ", e);
        }
    }

    private boolean skipIngest(JdbcTemplate jdbcTemplate) {
        if (ragConfigData.isForceRebuild()){
            String sql = "TRUNCATE TABLE " + quoteIdentifier(ragVectorStoreTableName);
            jdbcTemplate.update(sql);
        }else {
            Integer count=
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM"+
                                    ragVectorStoreTableName,
                            Integer.class
                    );
            if (count != null && count > 0){
                return true;
            }
        }
        return false;
    }
    private static String quoteIdentifier(String ident) {
        if (ident == null || !ident.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name: " + ident);
        }
        return "\"" + ident + "\"";
    }

    private void ingestDocumentChunksToVectorStore(Resource[] pdfResources) {
        var documents = getDocuments(pdfResources);
        var chunks = getChunks(documents);
        addChunksIndex(chunks);
        vectorStore.add(chunks);
        log.info("Ingested {} into PgVector", chunks.size());
    }



    private List<Document> getDocuments(Resource[] resources){
        List<Document> documents= new ArrayList<>();
        for (Resource pdfResource:resources){
            List<Document> parts = getDocumentsParts(pdfResource);
            addMetadata(pdfResource, parts);
            documents.addAll(parts);
        }
        return documents;
    }

    private void addMetadata(Resource pdfResource, List<Document> parts) {
        for (var part: parts){
            part.getMetadata().putIfAbsent(RagConstant.SOURCE, Objects.requireNonNull(pdfResource.getFilename()));
            part.getMetadata().putIfAbsent(RagConstant.DOC_TYPE, Objects.requireNonNull(pdfResource.getFilename().substring(0, pdfResource.getFilename().indexOf("."))));
            part.getMetadata().putIfAbsent(RagConstant.UPDATED_AT, ZonedDateTime.now().toLocalDate().toString());
        }
    }

    private List<Document> getDocumentsParts(Resource pdfResource) {
        List<Document> parts;
        if (RagConstant.PARAGRAPH.equalsIgnoreCase(ragConfigData.getPdfProperties().getMode())){
            parts = new PagePdfDocumentReader(pdfResource, pdfDocumentReaderConfig).read();
        }else {
            parts = new PagePdfDocumentReader(pdfResource, pdfDocumentReaderConfig).read();
        }
        return parts;
    }

    private List<Document> getChunks(List<Document> documents) {
        return tokenTextSplitter.apply(documents);
    }

    private void addChunksIndex(List<Document> chunks) {
        Map<String , Integer> counters = new HashMap<>();
        for (var chunk : chunks){
            var source = String.valueOf(chunk.getMetadata().getOrDefault(RagConstant.SOURCE, RagConstant.UPDATED_AT));
            var index = counters.merge(source, 1, Integer::sum) -1 ;
            chunk.getMetadata().put(RagConstant.CHUNK_INDEX, index);
        }
    }
}
