package com.gen.ai;

import com.gen.ai.service.RagIngestionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RagAssistantApplication implements CommandLineRunner {

    private final RagIngestionService ragIngestionService;

    public RagAssistantApplication(RagIngestionService ragIngestionService) {
        this.ragIngestionService = ragIngestionService;
    }

    public static void main(String[] args) {
		SpringApplication.run(RagAssistantApplication.class, args);
	}

    @Override
    public void run(String... args) throws Exception {
        ragIngestionService.initializePgVectorStore();
    }
}
