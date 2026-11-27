
package com.gen.ai.config;

import com.gen.ai.config.data.RagConfigData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class SimpleRagConfiguration {

    private static final String FILE_PATH = "classpath:/files/*.md";

    private static final String VECTOR_STORE_PATH =
            "src/main/resources/vector-store/vector.json";

    @Bean
    public TokenTextSplitter tokenTextSplitter(
            RagConfigData ragConfigData) {

        return TokenTextSplitter.builder()
                .withChunkSize(ragConfigData.getChunk().getSize())
                .withMinChunkSizeChars(
                        ragConfigData.getChunk().getMinChunkSize())
                .withMinChunkLengthToEmbed(
                        ragConfigData.getChunk().getMinChunkToEmbed())
                .withMaxNumChunks(
                        ragConfigData.getChunk().getMaxNumChunks())
                .withKeepSeparator(
                        ragConfigData.getChunk().isKeepSeparator())
                .build();
    }

    @Bean
    @Primary
    public SimpleVectorStore simpleVectorStore(
            @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel,
            TokenTextSplitter tokenTextSplitter,
            RagConfigData ragConfigData) throws IOException {

        SimpleVectorStore vectorStore =
                SimpleVectorStore.builder(embeddingModel).build();

        File vectorStoreFile = new File(VECTOR_STORE_PATH);

        if (vectorStoreFile.exists()
                && !ragConfigData.isForceRebuild()) {

            log.info("Loading existing vector store");

            vectorStore.load(vectorStoreFile);
            return vectorStore;
        }

        List<Document> documents = loadDocuments();

        log.info("Total documents loaded: {}", documents.size());

        List<Document> chunks =
                tokenTextSplitter.apply(documents);

        log.info("Total chunks created: {}", chunks.size());

        if (chunks.isEmpty()) {
            log.warn("No chunks generated. Check your markdown files.");
            return vectorStore;
        }

        vectorStore.add(chunks);

        vectorStoreFile.getParentFile().mkdirs();

        vectorStore.save(vectorStoreFile);

        log.info("Vector store saved at: {}",
                vectorStoreFile.getAbsolutePath());

        return vectorStore;
    }

    private List<Document> loadDocuments() throws IOException {

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(FILE_PATH);

        List<Document> documents = new ArrayList<>();

        for (Resource resource : resources) {

            TextReader reader = new TextReader(resource);

            reader.getCustomMetadata().put(
                    "category",
                    resource.getFilename().replace(".md", "")
            );

            reader.getCustomMetadata().put(
                    "access_level", "public"
            );

            reader.getCustomMetadata().put(
                    "version", "2026.01"
            );

            documents.addAll(reader.read());
        }

        return documents;
    }
}
