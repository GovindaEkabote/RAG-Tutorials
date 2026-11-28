package com.gen.ai.config.data;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.rag")
public class RagConfigData {

    private boolean forceRebuild;
    private int topK;
    private double similarityThreshold;
    private ChunkProperties chunk;
    private PdfProperties pdfProperties;
    private Map<String, String> synonyms;
    private int radius;
    private QueryExpansion queryExpansion;
    private Rerank rerank;


    @Data
    public static class ChunkProperties {

        private int size;
        private int minChunkSize;
        private int minChunkToEmbed;
        private int maxNumChunks;
        private boolean keepSeparator;
    }

    @Data
    public static class PdfProperties{
        private String mode;
        private String path;
        private int pagesPerDocument;
        private boolean leftAlignment;
        private int numberOfTopTextLinesToDelete;
        private int numberOfBottomTextLinesToDelete;
    }

    @Data
    public static class QueryExpansion{
        private int numberOfQueries;
    }

    public static class Rerank{
        private String url;
        private String apiKey;
        private String model;
        private int topN;
    }

}