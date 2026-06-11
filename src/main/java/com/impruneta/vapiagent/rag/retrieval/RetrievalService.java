package com.impruneta.vapiagent.rag.retrieval;

import com.impruneta.vapiagent.rag.embedding.EmbeddingService;
import com.impruneta.vapiagent.rag.embedding.EmbeddingVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Retrieves the most semantically relevant document chunks for a given natural language query.
 *
 * How it works:
 *   1. Embed the query string using EmbeddingService (same model used during indexing)
 *   2. Execute a cosine similarity search via pgvector's <=> operator using JdbcTemplate
 *   3. Return the top-k results ordered by descending similarity score
 *
 * The SQL uses the cosine distance operator (<=>):
 *   - Lower distance  = higher similarity
 *   - score = 1 - distance, so higher score = better match
 *   - The query embedding is passed twice: once for ORDER BY, once for the score expression
 *
 * Why JdbcTemplate?
 *   The pgvector <=> operator is not expressible in JPQL or the JPA Criteria API.
 *   JdbcTemplate with a raw SQL string is the simplest, most maintainable option.
 *   (Same rationale as EmbeddingVectorRepository.)
 *
 * Usage in the voicebot flow:
 *   - Vapi function call → POST body with user's question
 *   - retrieveMunicipalInfo(question, 5) returns top-5 chunks
 *   - Chunks are injected as context into the LLM prompt
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    /**
     * Cosine similarity search query.
     *
     * The same vector literal is passed for both ? parameters:
     *   1st ? → used in the score expression (1 - distance)
     *   2nd ? → used in the ORDER BY clause for index-assisted sorting
     * The 3rd ? → LIMIT (topK)
     *
     * The HNSW index (idx_municipal_chunk_embedding) on the embedding column
     * will be used automatically by PostgreSQL for the ORDER BY ... LIMIT pattern.
     */
    private static final String SIMILARITY_SQL = """
            SELECT id,
                   document_id,
                   content,
                   source_url,
                   title,
                   service_type,
                   1 - (embedding <=> ?::vector) AS score
            FROM municipal_document_chunk
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;

    public RetrievalService(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    /**
     * Returns the top-k most semantically similar chunks for the given query.
     *
     * @param query natural language question from the user/voicebot
     * @param topK  maximum number of chunks to return (typically 3–10)
     * @return list of results ordered by descending similarity score
     */
    public List<RetrievalResult> retrieveMunicipalInfo(String query, int topK) {
        log.debug("Retrieving top {} chunks for query: '{}'", topK, query);

        float[] queryEmbedding = embeddingService.embed(query);
        String vectorLiteral = EmbeddingVectorRepository.toVectorLiteral(queryEmbedding);

        List<RetrievalResult> results = jdbcTemplate.query(
            SIMILARITY_SQL,
            (rs, rowNum) -> new RetrievalResult(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("content"),
                rs.getString("source_url"),
                rs.getString("title"),
                rs.getString("service_type"),
                rs.getDouble("score")
            ),
            vectorLiteral, vectorLiteral, topK
        );

        log.debug("Retrieval returned {} results (top score: {})",
            results.size(),
            results.isEmpty() ? "n/a" : String.format("%.4f", results.get(0).score()));

        return results;
    }
}
