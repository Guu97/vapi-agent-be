package com.impruneta.vapiagent.vapi.dto;

import java.util.List;

/**
 * The response body that Vapi expects from a custom tool webhook.
 *
 * Vapi injects the "result" string into the LLM's context as the tool call output.
 * The toolCallId must echo back the id received from the incoming request.
 *
 * Expected shape:
 * {
 *   "results": [
 *     {
 *       "toolCallId": "call_abc123",
 *       "result": "Gli uffici dell'Anagrafe sono aperti dal lunedì al venerdì..."
 *     }
 *   ]
 * }
 */
public record VapiToolCallResponse(List<ToolResult> results) {

    public record ToolResult(String toolCallId, String result) {
    }

    /**
     * Convenience factory for the common v1 case: one tool call, one result string.
     */
    public static VapiToolCallResponse of(String toolCallId, String result) {
        return new VapiToolCallResponse(List.of(new ToolResult(toolCallId, result)));
    }
}
