package com.impruneta.vapiagent.rag.embedding;

/**
 * Abstraction for generating dense embedding vectors from text.
 *
 * Contract:
 *   - Input: a non-null, non-blank text string (pre-processed chunk content)
 *   - Output: a float array of exactly the configured embedding dimension
 *
 * The dimension MUST match the pgvector column definition in the database:
 *     embedding extensions.vector(768)
 *
 * Concrete implementation: OpenAiEmbeddingService (text-embedding-3-small, dimensions=768).
 * Swap the implementation bean to use a different provider without changing callers.
 */
public interface EmbeddingService {

    /**
     * Generates an embedding vector for the given text.
     *
     * @param text the text to embed; should be clean and trimmed
     * @return float array of length matching the configured dimension (768)
     * @throws RuntimeException if the provider call fails (network error, quota exceeded, etc.)
     */
    float[] embed(String text);
}
