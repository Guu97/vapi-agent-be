package com.impruneta.vapiagent.calllog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stores the end-of-call report sent by Vapi at the conclusion of each call.
 *
 * The vapi_call_id column has a UNIQUE constraint enforced at the DB level.
 * The service layer handles duplicate inserts gracefully (skip-if-exists).
 */
@Entity
@Table(name = "call_log")
public class CallLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "vapi_call_id", nullable = false, length = 100, unique = true)
    private String vapiCallId;

    @Column(name = "transcript", nullable = false, columnDefinition = "text")
    private String transcript;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CallLog() {
        // Required by JPA
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVapiCallId() { return vapiCallId; }
    public void setVapiCallId(String vapiCallId) { this.vapiCallId = vapiCallId; }

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
