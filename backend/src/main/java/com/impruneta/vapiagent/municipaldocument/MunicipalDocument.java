package com.impruneta.vapiagent.municipaldocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A scraped web page from the Comune di Impruneta website.
 *
 * Design note on the 'embedding' column:
 *   The database column 'embedding' is of type extensions.vector(768) – a pgvector type.
 *   This type is NOT natively supported by Hibernate/JPA without the pgvector-hibernate
 *   extension (which introduces version coupling and extra configuration).
 *
 *   Decision: the 'embedding' column is intentionally NOT mapped here.
 *   All vector reads and writes are handled by EmbeddingVectorRepository using JdbcTemplate
 *   with raw SQL casts: UPDATE ... SET embedding = ?::vector WHERE id = ?
 *
 *   This keeps the JPA entity clean and makes the vector I/O path explicit and easy to debug.
 */
@Entity
@Table(name = "municipal_document")
public class MunicipalDocument {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_url", nullable = false, unique = true)
    private String sourceUrl;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "service_type", length = 100)
    private String serviceType;

    @Column(name = "raw_html", columnDefinition = "text")
    private String rawHtml;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public MunicipalDocument() {
        // Required by JPA and used by IngestionService
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getRawHtml() { return rawHtml; }
    public void setRawHtml(String rawHtml) { this.rawHtml = rawHtml; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
