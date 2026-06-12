package com.impruneta.vapiagent.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * EmbeddingService implementation backed by the OpenAI Embeddings API.
 *
 * Model choice: text-embedding-3-small
 *   - Supports the "dimensions" parameter, allowing us to request exactly 768 dimensions
 *     to match the pgvector column: embedding extensions.vector(768)
 *   - Cost-effective and high-quality for multilingual Italian text
 *   - Outperforms ada-002 on most retrieval benchmarks at lower cost
 *
 * Why RestClient instead of the OpenAI Java SDK?
 *   - The official Java SDK adds many transitive dependencies
 *   - Spring's RestClient (available since Spring Boot 3.2 / Spring 6.1) is already
 *     on the classpath
 *   - The /v1/embeddings endpoint has a simple, stable request/response shape –
 *     no need for a full SDK for one API call
 *
 * Inject via environment variable: OPENAI_API_KEY
 * The app.rag.embedding.openai-api-key property defaults to "changeme" in application.yml,
 * which will cause a clear 401 error from the API (not a silent misconfiguration).
 */
@Service
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private static final String OPENAI_BASE_URL = "https://api.openai.com";
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final RestClient restClient;

    public OpenAiEmbeddingService(
        @Value("${app.rag.embedding.openai-api-key}") String apiKey,
        @Value("${app.rag.embedding.model}") String model,
        @Value("${app.rag.embedding.dimensions}") int dimensions
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.restClient = RestClient.builder()
            .baseUrl(OPENAI_BASE_URL)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public float[] embed(String text) {
        EmbeddingRequest request = new EmbeddingRequest(text, model, dimensions);

        EmbeddingApiResponse response = restClient.post()
            .uri(EMBEDDINGS_PATH)
            .header("Authorization", "Bearer " + apiKey)
            .body(request)
            .retrieve()
            .body(EmbeddingApiResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI embeddings API returned an empty response");
        }

        List<Double> values = response.data().get(0).embedding();
        if (values == null || values.size() != dimensions) {
            throw new IllegalStateException(
                "Unexpected embedding dimension from API: expected=" + dimensions +
                ", got=" + (values == null ? "null" : values.size())
            );
        }

        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }

        log.debug("Embedded text (len={}) → vector({})", text.length(), result.length);
        return result;
    }

    // -------------------------------------------------------------------------
    // OpenAI API request / response shapes
    // These are private records – not part of the public API of this class.
    // -------------------------------------------------------------------------

    /**
     * Request body for POST /v1/embeddings.
     * Jackson serialises record components as JSON fields by their name.
     */
    private record EmbeddingRequest(String input, String model, int dimensions) {
    }

    /**
     * Top-level response wrapper from /v1/embeddings.
     * Only "data" is mapped; other fields (object, model, usage) are ignored by Jackson.
     */
    private record EmbeddingApiResponse(List<EmbeddingData> data) {
    }

    /**
     * A single embedding object inside the "data" array.
     * Only "embedding" is mapped; "object" and "index" are ignored.
     */
    private record EmbeddingData(List<Double> embedding) {
    }
}
