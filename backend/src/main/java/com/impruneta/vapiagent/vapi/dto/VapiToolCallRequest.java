package com.impruneta.vapiagent.vapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Represents the webhook request body that Vapi sends when a custom tool is invoked.
 *
 * Vapi webhook structure (relevant fields only):
 * {
 *   "message": {
 *     "type": "tool-calls",
 *     "toolCallList": [
 *       {
 *         "id": "call_abc123",
 *         "type": "function",
 *         "function": {
 *           "name": "retrieve-municipal-info",
 *           "arguments": "{\"query\": \"orari ufficio anagrafe\"}"
 *         }
 *       }
 *     ]
 *   }
 * }
 *
 * Notes:
 *   - "arguments" is a JSON-encoded string (not a nested object). This is the OpenAI
 *     tool call format that Vapi inherits. VapiToolsAdapterService parses it with ObjectMapper.
 *   - @JsonIgnoreProperties(ignoreUnknown = true) is applied at every nesting level because
 *     Vapi includes many additional fields (call, artifact, org, timestamp, etc.) we don't need.
 *   - Only the first item in toolCallList is processed in v1 (one tool call per webhook).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VapiToolCallRequest(VapiMessage message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VapiMessage(List<VapiToolCall> toolCallList) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VapiToolCall(String id, VapiFunction function) {
    }

    /**
     * @param name      tool name as configured in the Vapi dashboard (e.g. "book-appointment")
     * @param arguments tool parameters — accepted as either a JSON-encoded String
     *                  (Vapi production: {@code "arguments": "{\"query\":\"...\"}"})
     *                  or a plain JSON Object (test/curl: {@code "arguments": {"query":"..."}})
     *                  VapiToolsAdapterService.parseArgsAsMap() normalises both forms.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VapiFunction(String name, JsonNode arguments) {
    }
}
