package com.gen.ai.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties("spring.ai.vectorstore.pgvector")
public class PgVectorConfigData {

    private String tableNameForChatMemory;
    private String tableNameForRag;
    private boolean initializeSchema;
    private Integer dimensions;
    private String indexType;
    private String distanceType;
}
