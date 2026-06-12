package com.impruneta.vapiagent.municipaldocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A fixed-size text chunk derived from a MunicipalDocument.
 *
 * Each chunk is a semantically coherent portion of a web page, produced by
 * the paragraph-based chunking algorithm in ChunkingService.
 *
 * Design note on the 'embedding' column:
 *   Same rationale as MunicipalDocument – the pgvector column is NOT mapped here.
 *   EmbeddingVectorRepository handles all vector I/O via JdbcTemplate.
 *
 *   As a consequence, findAllWithNullEmbedding() uses a native SQL query rather than JPQL,
 *   since JPQL cannot reference unmapped columns.
 */
@Entity
@Table(name = "municipal_document_chunk")
public class MunicipalDocumentChunk {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "title")
    private String title;

    @Column(name = "service_type", length = 100)
    private String serviceType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public MunicipalDocumentChunk() {
        // Required by JPA and used by ChunkingTransactionHelper
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
