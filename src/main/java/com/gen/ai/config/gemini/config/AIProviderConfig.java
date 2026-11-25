package com.gen.ai.config.gemini.config;

import com.gen.ai.config.data.RagConfigData;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AIProviderConfig {

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
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
}