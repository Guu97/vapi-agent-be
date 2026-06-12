package com.impruneta.vapiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Comune di Impruneta Vapi Voicebot backend.
 *
 * This is a single Spring Boot application that combines:
 *   - Standard domain/business logic (appointments, service types)
 *   - RAG pipeline (ingestion, chunking, embedding, retrieval)
 *
 * @ConfigurationPropertiesScan automatically registers any class annotated
 * with @ConfigurationProperties found anywhere in this package tree.
 * Currently used by: ChunkingProperties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VapiAgentBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VapiAgentBeApplication.class, args);
    }
}
