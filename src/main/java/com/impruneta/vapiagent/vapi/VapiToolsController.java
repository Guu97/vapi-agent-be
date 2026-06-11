package com.impruneta.vapiagent.vapi;

import com.impruneta.vapiagent.vapi.dto.VapiToolCallRequest;
import com.impruneta.vapiagent.vapi.dto.VapiToolCallResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes Vapi custom tool webhooks as HTTP endpoints.
 *
 * How Vapi custom tools work:
 *   1. In the Vapi dashboard, define a tool with "type: function" and set
 *      "server.url" to the corresponding endpoint below.
 *   2. When the LLM decides to invoke the tool during a live call, Vapi sends a
 *      POST request to that URL with the tool call payload in its webhook format.
 *   3. This controller extracts the first tool call, delegates all logic to
 *      VapiToolsAdapterService, and returns the result in Vapi's expected shape:
 *        { "results": [{ "toolCallId": "...", "result": "..." }] }
 *   4. Vapi injects the "result" string as the tool call output into the LLM context.
 *
 * Endpoints:
 *   POST /api/vapi/tools/retrieve-municipal-info
 *   POST /api/vapi/tools/book-appointment
 *   POST /api/vapi/tools/check-appointment-availability
 *   POST /api/vapi/tools/get-appointments-by-name
 *   POST /api/vapi/tools/cancel-appointment
 *
 * Security note:
 *   In production, validate the x-vapi-secret header to verify the request comes from Vapi.
 *   Add a servlet filter or a @RequestHeader("x-vapi-secret") parameter check with a
 *   shared secret stored in application.yml. Left out here to keep the scope minimal.
 */
@RestController
@RequestMapping("/api/vapi/tools")
public class VapiToolsController {

    private static final Logger log = LoggerFactory.getLogger(VapiToolsController.class);

    private final VapiToolsAdapterService adapterService;

    public VapiToolsController(VapiToolsAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    /**
     * Vapi tool: retrieve-municipal-info
     *
     * Invoked when the LLM needs to look up information about municipal services,
     * office hours, administrative procedures, social services, etc.
     */
    @PostMapping("/retrieve-municipal-info")
    public ResponseEntity<VapiToolCallResponse> retrieveMunicipalInfo(
        @RequestBody VapiToolCallRequest request
    ) {
        VapiToolCallRequest.VapiToolCall toolCall = extractFirstToolCall(request);
        if (toolCall == null) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Vapi tool call – name='{}', id='{}'", toolCall.function().name(), toolCall.id());

        return ResponseEntity.ok(
            adapterService.handleRetrieveMunicipalInfo(
                toolCall.id(),
                toolCall.function().arguments()
            )
        );
    }

    /**
     * Vapi tool: book-appointment
     *
     * Invoked when the LLM has collected all required booking information from the
     * citizen and is ready to persist the appointment record.
     */
    @PostMapping("/book-appointment")
    public ResponseEntity<VapiToolCallResponse> bookAppointment(
        @RequestBody VapiToolCallRequest request
    ) {
        VapiToolCallRequest.VapiToolCall toolCall = extractFirstToolCall(request);
        if (toolCall == null) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Vapi tool call – name='{}', id='{}'", toolCall.function().name(), toolCall.id());

        return ResponseEntity.ok(
            adapterService.handleBookAppointment(
                toolCall.id(),
                toolCall.function().arguments()
            )
        );
    }

    /**
     * Vapi tool: check-appointment-availability
     *
     * Checks whether a specific slot (serviceTypeCode + date + time) is free.
     * Should be called by the LLM BEFORE book-appointment to avoid booking conflicts.
     */
    @PostMapping("/check-appointment-availability")
    public ResponseEntity<VapiToolCallResponse> checkAppointmentAvailability(
        @RequestBody VapiToolCallRequest request
    ) {
        VapiToolCallRequest.VapiToolCall toolCall = extractFirstToolCall(request);
        if (toolCall == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Vapi tool call – name='{}', id='{}'", toolCall.function().name(), toolCall.id());
        return ResponseEntity.ok(
            adapterService.handleCheckAvailability(
                toolCall.id(),
                toolCall.function().arguments()
            )
        );
    }

    /**
     * Vapi tool: get-appointments-by-name
     *
     * Returns all active appointments for a citizen identified by name.
     * Partial case-insensitive match: works when the citizen provides only their surname.
     */
    @PostMapping("/get-appointments-by-name")
    public ResponseEntity<VapiToolCallResponse> getAppointmentsByName(
        @RequestBody VapiToolCallRequest request
    ) {
        VapiToolCallRequest.VapiToolCall toolCall = extractFirstToolCall(request);
        if (toolCall == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Vapi tool call – name='{}', id='{}'", toolCall.function().name(), toolCall.id());
        return ResponseEntity.ok(
            adapterService.handleGetAppointmentsByName(
                toolCall.id(),
                toolCall.function().arguments()
            )
        );
    }

    /**
     * Vapi tool: cancel-appointment
     *
     * Cancels a BOOKED appointment identified by citizenEmail + serviceTypeCode + appointmentDate.
     * Voice-friendly: the citizen does not need to know their internal UUID.
     */
    @PostMapping("/cancel-appointment")
    public ResponseEntity<VapiToolCallResponse> cancelAppointment(
        @RequestBody VapiToolCallRequest request
    ) {
        VapiToolCallRequest.VapiToolCall toolCall = extractFirstToolCall(request);
        if (toolCall == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Vapi tool call – name='{}', id='{}'", toolCall.function().name(), toolCall.id());
        return ResponseEntity.ok(
            adapterService.handleCancelAppointment(
                toolCall.id(),
                toolCall.function().arguments()
            )
        );
    }

    /**
     * Pulls the first tool call out of the Vapi webhook message.
     * Returns null (and logs a warning) if the message or toolCallList is absent/empty,
     * which causes the controller to respond with HTTP 400.
     */
    private VapiToolCallRequest.VapiToolCall extractFirstToolCall(VapiToolCallRequest request) {
        if (request == null || request.message() == null) {
            log.warn("Received a Vapi webhook with a null message body");
            return null;
        }
        List<VapiToolCallRequest.VapiToolCall> list = request.message().toolCallList();
        if (list == null || list.isEmpty()) {
            log.warn("Received a Vapi webhook with an empty toolCallList");
            return null;
        }
        return list.get(0);
    }
}
