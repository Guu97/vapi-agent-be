package com.impruneta.vapiagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test that verifies the Spring application context loads successfully.
 *
 * The test uses an in-memory datasource override so it can run without
 * a live Supabase connection in CI. Schema validation is disabled via ddl-auto=none
 * (already set in application.yml), so no tables need to exist.
 *
 * Note: this test will fail if the OPENAI_API_KEY environment variable is not set,
 * because OpenAiEmbeddingService reads it at construction time. The @TestPropertySource
 * below injects a dummy key so the context can start (actual API calls are never made).
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.rag.embedding.openai-api-key=test-key-not-real",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class VapiAgentBeApplicationTests {

    @Test
    void contextLoads() {
        // If the application context starts without throwing, this test passes.
        // This verifies: bean wiring, @ConfigurationProperties bindings, @Value injections.
    }
}
