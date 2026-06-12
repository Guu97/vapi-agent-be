package com.impruneta.vapiagent.municipaldocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface MunicipalDocumentChunkRepository extends JpaRepository<MunicipalDocumentChunk, UUID> {

    /**
     * Bulk-deletes all chunks for a given document before re-chunking.
     *
     * Uses a JPQL bulk delete (single UPDATE statement, not the load-then-delete pattern)
     * for efficiency. clearAutomatically=true evicts the affected entities from the
     * first-level JPA cache so that subsequent reads see the updated state.
     *
     * Must be called within an active transaction (e.g. from ChunkingHelper).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM MunicipalDocumentChunk c WHERE c.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * Finds all chunks that have not yet received an embedding vector.
     *
     * Uses a native SQL query because the 'embedding' column is intentionally NOT mapped
     * in the MunicipalDocumentChunk JPA entity. JPQL cannot reference unmapped columns.
     *
     * Hibernate safely ignores the unmapped 'embedding' column when constructing entity
     * instances from the result set – only mapped columns are read.
     *
     * Note: explicitly lists only the mapped columns to avoid any type-conversion attempt
     * on the extensions.vector type by the JDBC driver during result set traversal.
     */
    @Query(
        value = """
            SELECT id, document_id, chunk_index, title, service_type,
                   content, source_url, created_at
            FROM municipal_document_chunk
            WHERE embedding IS NULL
            ORDER BY created_at
            """,
        nativeQuery = true
    )
    List<MunicipalDocumentChunk> findAllWithNullEmbedding();
}
