package com.impruneta.vapiagent.calllog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * Represents the Vapi end-of-call-report webhook payload.
 *
 * Expected structure:
 * {
 *   "message": {
 *     "type": "end-of-call-report",
 *     "call": {
 *       "id": "call_xxx",
 *       "startedAt": "...",
 *       "endedAt": "..."
 *     },
 *     "transcript": "...",
 *     "summary": "...",
 *     "durationSeconds": 123
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VapiEndOfCallReportRequest(VapiEndOfCallMessage message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VapiEndOfCallMessage(
            String type,
            VapiCallInfo call,
            String transcript,
            String summary,
            Integer durationSeconds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VapiCallInfo(
            String id,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt
    ) {}
}
