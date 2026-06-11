package com.impruneta.vapiagent.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Handles all pgvector-specific database operations that cannot be expressed via JPA.
 *
 * Why JdbcTemplate instead of JPA / pgvector-hibernate?
 *
 *   The 'embedding' column is of type extensions.vector(768) – a custom PostgreSQL type
 *   provided by the pgvector extension. Hibernate's standard type system does not know
 *   how to map Java float[] to this column without additional extensions (pgvector-hibernate),
 *   which introduces version coupling, configuration complexity, and risk of silent type errors.
 *
 *   The alternative used here is explicit raw SQL with a type cast:
 *       UPDATE ... SET embedding = ?::vector WHERE id = ?
 *   The string parameter "[0.1,0.2,...,0.768]" is the pgvector text literal format.
 *   PostgreSQL parses and validates it during the cast. If the dimension doesn't match
 *   the column definition, PostgreSQL raises an error immediately.
 *
 *   This approach is:
 *   - Zero external dependencies beyond the PostgreSQL JDBC driver
 *   - Completely explicit – there is no magic type mapping
 *   - Easy to explain and understand
 *   - Easy to test: the SQL string is readable
 */
@Repository
public class EmbeddingVectorRepository {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingVectorRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes an embedding vector to the municipal_document_chunk row with the given ID.
     *
     * The float array is formatted as a pgvector string literal ("[f1,f2,...,fn]")
     * and passed as a VARCHAR parameter, which PostgreSQL casts to vector(768).
     * PostgreSQL will raise an error if the array length != the column's declared dimension.
     *
     * @param chunkId   UUID of the chunk to update
     * @param embedding float array; length must equal the pgvector column dimension (768)
     * @throws IllegalStateException if no row was found with the given ID
     */
    public void updateEmbedding(UUID chunkId, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);
        int updated = jdbcTemplate.update(
            "UPDATE municipal_document_chunk SET embedding = ?::vector WHERE id = ?",
            vectorLiteral,
            chunkId
        );
        if (updated == 0) {
            throw new IllegalStateException("No chunk row found for id: " + chunkId);
        }
        log.debug("Persisted embedding vector({}) for chunk {}", embedding.length, chunkId);
    }

    /**
     * Formats a float array as a pgvector string literal.
     *
     * Example: [0.1f, -0.2f, 0.3f] → "[0.1,-0.2,0.3]"
     *
     * PostgreSQL's pgvector accepts this format when cast with ::vector.
     */
    public static String toVectorLiteral(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
