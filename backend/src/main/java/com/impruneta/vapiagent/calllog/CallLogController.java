package com.impruneta.vapiagent.calllog;

import com.impruneta.vapiagent.calllog.dto.CallLogResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint for the frontend dashboard to retrieve conversation logs.
 *
 * Endpoint:
 *   GET /api/call-logs?limit=20
 */
@RestController
@RequestMapping("/api/call-logs")
public class CallLogController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;

    private final CallLogService callLogService;

    public CallLogController(CallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @GetMapping
    public ResponseEntity<List<CallLogResponse>> getRecentLogs(
            @RequestParam(name = "limit", required = false) Integer limit) {

        int effectiveLimit = (limit != null && limit > 0)
                ? Math.min(limit, MAX_LIMIT)
                : DEFAULT_LIMIT;

        return ResponseEntity.ok(callLogService.getRecentLogs(effectiveLimit));
    }
}
