package com.impruneta.vapiagent.calllog;

import com.impruneta.vapiagent.calllog.dto.VapiEndOfCallReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Vapi end-of-call webhook events and persists them as call logs.
 *
 * Endpoint:
 *   POST /api/vapi/webhook/call-ended
 *
 * Only "end-of-call-report" message types are persisted. All other types are
 * acknowledged with HTTP 200 without any side effects.
 */
@RestController
@RequestMapping("/api/vapi/webhook")
public class VapiWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VapiWebhookController.class);
    private static final String END_OF_CALL_REPORT = "end-of-call-report";

    private final CallLogService callLogService;

    public VapiWebhookController(CallLogService callLogService) {
        this.callLogService = callLogService;
    }

    @PostMapping("/call-ended")
    public ResponseEntity<Void> handleCallEnded(@RequestBody VapiEndOfCallReportRequest request) {
        if (request.message() == null) {
            log.warn("Received webhook with null message — ignoring");
            return ResponseEntity.ok().build();
        }

        String messageType = request.message().type();

        if (!END_OF_CALL_REPORT.equals(messageType)) {
            log.debug("Ignoring webhook message of type={}", messageType);
            return ResponseEntity.ok().build();
        }

        log.info("Received end-of-call-report for call.id={}",
                request.message().call() != null ? request.message().call().id() : "unknown");

        callLogService.saveEndOfCallReport(request.message());

        return ResponseEntity.ok().build();
    }
}
