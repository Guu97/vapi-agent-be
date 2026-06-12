package com.impruneta.vapiagent.calllog.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO returned by GET /api/call-logs.
 * Exposes call log data for the frontend dashboard.
 */
public record CallLogResponse(
        UUID id,
        String vapiCallId,
        String summary,
        String transcript,
        Integer durationSeconds,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt
) {}
