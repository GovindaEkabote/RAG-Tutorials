package com.gen.ai.config.gemini.config;

import com.gen.ai.config.PgVectorConfigData;
import com.gen.ai.config.data.RagConfigData;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AIProviderConfig {

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean("chatMemoryVectorStore")
    public VectorStore chatMemoryVectorStore(
            JdbcTemplate jdbcTemplate,
            PgVectorConfigData pgVectorConfigData,
            @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel
    ){
        return PgVectorStore.builder(
                jdbcTemplate,embeddingModel
        ).vectorTableName(pgVectorConfigData.getTableNameForChatMemory())
                .initializeSchema(pgVectorConfigData.isInitializeSchema())
                .dimensions(pgVectorConfigData.getDimensions())
                .indexType(PgVectorStore.PgIndexType.valueOf(pgVectorConfigData.getIndexType()))
                .distanceType(PgVectorStore.PgDistanceType.valueOf(pgVectorConfigData.getDistanceType()))
                .build();
    }

    @Bean("ragVectorStore")
    public VectorStore ragVectorStore(
            JdbcTemplate jdbcTemplate,
            PgVectorConfigData pgVectorConfigData,
            @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel
    ){
        return PgVectorStore.builder(
                        jdbcTemplate,embeddingModel
                ).vectorTableName(pgVectorConfigData.getTableNameForRag())
                .initializeSchema(pgVectorConfigData.isInitializeSchema())
                .dimensions(pgVectorConfigData.getDimensions())
                .indexType(PgVectorStore.PgIndexType.valueOf(pgVectorConfigData.getIndexType()))
                .distanceType(PgVectorStore.PgDistanceType.valueOf(pgVectorConfigData.getDistanceType()))
                .build();
    }

    @Bean
    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            @Qualifier("ragVectorStore") VectorStore vectorStore,
            RagConfigData ragConfigData
    ){

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(ragConfigData.getTopK())
                        .similarityThreshold(ragConfigData.getSimilarityThreshold())
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();

    }

    @Bean
    public TokenTextSplitter aiProviderTokenTextSplitter(
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

    @Bean("geminiAIRAGChatClient")
    ChatClient geminiRAGChatClient(
            GoogleGenAiChatModel googleGenAiChatModel,
            SimpleVectorStore simpleVectorStore,
            SimpleLoggerAdvisor simpleLoggerAdvisor,
            RagConfigData ragConfigData
    ) {
        return ChatClient.builder(googleGenAiChatModel)
                .defaultAdvisors(
                        simpleLoggerAdvisor,
                        QuestionAnswerAdvisor.builder(simpleVectorStore)
                                .searchRequest(
                                        SearchRequest.builder()
                                                .topK(ragConfigData.getTopK())
                                                .similarityThreshold(
                                                        ragConfigData.getSimilarityThreshold()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
    }



    @Bean("googleAIAdvancedRAGChatClient")
    ChatClient GoogleAIAdvancedRAGChatClient(
            GoogleGenAiChatModel googleGenAiChatModel,
            RetrievalAugmentationAdvisor retrievalAugmentationAdvisor
    ){
        return ChatClient.builder(googleGenAiChatModel)
                .defaultAdvisors(retrievalAugmentationAdvisor)
                .build();
    }

    @Bean
    @Primary
    EmbeddingModel embeddingModel(
            @Qualifier("googleGenAiTextEmbedding") EmbeddingModel embeddingModel
    ){
        return embeddingModel;
    }



}