package com.impruneta.vapiagent.vapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impruneta.vapiagent.appointment.Appointment;
import com.impruneta.vapiagent.appointment.AppointmentBookingRequest;
import com.impruneta.vapiagent.appointment.AppointmentService;
import com.impruneta.vapiagent.rag.retrieval.RetrievalResult;
import com.impruneta.vapiagent.rag.retrieval.RetrievalService;
import com.impruneta.vapiagent.vapi.dto.VapiToolCallResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Adapter between the Vapi tool call format and the internal application services.
 *
 * Responsibilities:
 *   - Parse the JSON-encoded "arguments" string from each tool call into typed values
 *   - Validate required fields and return a safe Italian error string on bad input
 *   - Delegate to RetrievalService or AppointmentService
 *   - Format service output into a human-readable Italian string suitable for the LLM
 *   - Catch ALL exceptions: this service must never propagate an error that would cause
 *     Vapi to receive an HTTP 5xx. The LLM must always receive a meaningful result string.
 *
 * Why ObjectMapper for arguments parsing?
 *   Vapi sends "arguments" as a raw JSON-encoded string (OpenAI tool call convention).
 *   Deserialising with ObjectMapper.readValue() is the simplest, most explicit approach
 *   and requires no extra dependencies beyond Jackson (already on the classpath via Spring).
 */
@Service
public class VapiToolsAdapterService {

    private static final Logger log = LoggerFactory.getLogger(VapiToolsAdapterService.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final DateTimeFormatter ITALIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RetrievalService retrievalService;
    private final AppointmentService appointmentService;
    private final ObjectMapper objectMapper;

    public VapiToolsAdapterService(RetrievalService retrievalService,
                                    AppointmentService appointmentService,
                                    ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.appointmentService = appointmentService;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Tool: retrieve-municipal-info
    //
    // Vapi tool JSON schema (paste into Vapi dashboard → Tools → New Tool):
    // {
    //   "name": "retrieve-municipal-info",
    //   "description": "Cerca informazioni sui servizi del Comune di Impruneta. Usa questo strumento quando il cittadino chiede di orari, procedure, uffici o documenti.",
    //   "parameters": {
    //     "type": "object",
    //     "properties": {
    //       "query": {
    //         "type": "string",
    //         "description": "La domanda esatta dell'utente, nella sua lingua originale"
    //       },
    //       "topK": {
    //         "type": "integer",
    //         "description": "Numero massimo di risultati da restituire (default: 5)"
    //       }
    //     },
    //     "required": ["query"]
    //   },
    //   "server": { "url": "https://YOUR_HOST/api/vapi/tools/retrieve-municipal-info" }
    // }
    // =========================================================================

    /**
     * Handles the "retrieve-municipal-info" tool call.
     *
     * @param toolCallId echoed back in the Vapi response
     * @param arguments  JSON string — expected keys: query (required), topK (optional)
     */
    public VapiToolCallResponse handleRetrieveMunicipalInfo(String toolCallId, JsonNode argumentsNode) {
        Map<String, Object> args;
        try {
            args = parseArgsAsMap(argumentsNode);
        } catch (Exception e) {
            log.warn("[{}] retrieve-municipal-info: failed to parse arguments – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile elaborare la richiesta: parametri non validi.");
        }

        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            log.warn("[{}] retrieve-municipal-info: missing required argument 'query'", toolCallId);
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile trovare informazioni: il parametro 'query' è obbligatorio.");
        }

        int topK = DEFAULT_TOP_K;
        if (args.get("topK") instanceof Number n) {
            topK = n.intValue();
        }

        log.info("[{}] retrieve-municipal-info – query='{}', topK={}", toolCallId, query, topK);

        List<RetrievalResult> results;
        try {
            results = retrievalService.retrieveMunicipalInfo(query, topK);
        } catch (Exception e) {
            log.error("[{}] retrieve-municipal-info: retrieval error – {}", toolCallId, e.getMessage(), e);
            return VapiToolCallResponse.of(toolCallId,
                "Si è verificato un errore durante la ricerca delle informazioni. Riprova più tardi.");
        }

        if (results.isEmpty()) {
            return VapiToolCallResponse.of(toolCallId,
                "Non ho trovato informazioni pertinenti sul sito del Comune di Impruneta per questa domanda.");
        }

        return VapiToolCallResponse.of(toolCallId, formatRetrievalResults(results));
    }

    private String formatRetrievalResults(List<RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ecco le informazioni trovate sul sito del Comune di Impruneta:\n\n");
        for (int i = 0; i < results.size(); i++) {
            sb.append(results.get(i).content().trim());
            if (i < results.size() - 1) {
                sb.append("\n\n---\n\n");
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // Tool: book-appointment
    //
    // Vapi tool JSON schema (paste into Vapi dashboard → Tools → New Tool):
    // {
    //   "name": "book-appointment",
    //   "description": "Prenota un appuntamento con un ufficio del Comune di Impruneta. Usa questo strumento solo quando hai raccolto tutti i dati richiesti dal cittadino.",
    //   "parameters": {
    //     "type": "object",
    //     "properties": {
    //       "citizenName":     { "type": "string",  "description": "Nome e cognome del cittadino" },
    //       "citizenEmail":    { "type": "string",  "format": "email", "description": "Indirizzo email del cittadino" },
    //       "serviceTypeCode": {
    //         "type": "string",
    //         "enum": ["ANAGRAFE", "TRIBUTI", "URP", "SERVIZI_SOCIALI"],
    //         "description": "Codice dell'ufficio/servizio per cui si prenota"
    //       },
    //       "appointmentDate": { "type": "string", "format": "date", "description": "Data dell'appuntamento nel formato yyyy-MM-dd" },
    //       "appointmentTime": { "type": "string", "description": "Ora dell'appuntamento nel formato HH:mm" },
    //       "notes":           { "type": "string", "description": "Note o motivazione facoltative" }
    //     },
    //     "required": ["citizenName", "citizenEmail", "serviceTypeCode", "appointmentDate", "appointmentTime"]
    //   },
    //   "server": { "url": "https://YOUR_HOST/api/vapi/tools/book-appointment" }
    // }
    // =========================================================================

    /**
     * Handles the "book-appointment" tool call.
     *
     * @param toolCallId echoed back in the Vapi response
     * @param arguments  JSON string — expected keys: citizenName, citizenEmail, serviceTypeCode,
     *                   appointmentDate (yyyy-MM-dd), appointmentTime (HH:mm), notes (optional)
     */
    public VapiToolCallResponse handleBookAppointment(String toolCallId, JsonNode argumentsNode) {
        BookAppointmentArgs args;
        try {
            args = parseArgs(argumentsNode, BookAppointmentArgs.class);
        } catch (Exception e) {
            log.warn("[{}] book-appointment: failed to parse arguments – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile elaborare la prenotazione: parametri non validi.");
        }

        // Explicit required-field validation (no @Valid here – error message must be Italian)
        if (isBlank(args.citizenName()) || isBlank(args.citizenEmail())
                || isBlank(args.serviceTypeCode())
                || isBlank(args.appointmentDate()) || isBlank(args.appointmentTime())) {
            log.warn("[{}] book-appointment: one or more required fields are missing", toolCallId);
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile completare la prenotazione: mancano campi obbligatori. " +
                "Sono richiesti: nome, email, tipo di servizio, data e ora.");
        }

        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(args.appointmentDate());
            time = LocalTime.parse(args.appointmentTime());
        } catch (DateTimeParseException e) {
            log.warn("[{}] book-appointment: invalid date/time format – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Il formato della data o dell'ora non è valido. " +
                "Usare yyyy-MM-dd per la data (es. 2026-07-15) e HH:mm per l'ora (es. 10:30).");
        }

        if (!date.isAfter(LocalDate.now())) {
            log.warn("[{}] book-appointment: date {} is not in the future", toolCallId, date);
            return VapiToolCallResponse.of(toolCallId,
                "Non è possibile prenotare un appuntamento per una data passata o per oggi. " +
                "Scegliere una data futura.");
        }

        log.info("[{}] book-appointment – service={}, date={}, citizen={}",
            toolCallId, args.serviceTypeCode(), date, args.citizenName());

        AppointmentBookingRequest request = new AppointmentBookingRequest(
            args.citizenName(),
            args.citizenEmail(),
            args.serviceTypeCode(),
            date,
            time,
            args.notes()
        );

        Appointment booked;
        try {
            booked = appointmentService.book(request);
        } catch (IllegalArgumentException e) {
            // Known business error: e.g. unknown serviceTypeCode
            log.warn("[{}] book-appointment business error: {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Prenotazione non riuscita: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] book-appointment unexpected error: {}", toolCallId, e.getMessage(), e);
            return VapiToolCallResponse.of(toolCallId,
                "Si è verificato un errore durante la prenotazione. Riprova più tardi.");
        }

        // Short human-readable reference (first 8 chars of UUID, uppercase) — voice-friendly
        String shortRef = booked.getId().toString().substring(0, 8).toUpperCase();
        String formattedDate = booked.getAppointmentDate().format(ITALIAN_DATE);
        String formattedTime = booked.getAppointmentTime().toString();

        String confirmation = String.format(
            "Appuntamento prenotato con successo. " +
            "Codice di riferimento: %s. " +
            "Servizio: %s. " +
            "Data: %s alle %s. " +
            "Una conferma verrà inviata all'indirizzo %s.",
            shortRef,
            booked.getServiceTypeCode(),
            formattedDate,
            formattedTime,
            booked.getCitizenEmail()
        );

        log.info("[{}] Appointment booked – id={}, ref={}", toolCallId, booked.getId(), shortRef);
        return VapiToolCallResponse.of(toolCallId, confirmation);
    }

    // =========================================================================
    // Tool: check-appointment-availability
    //
    // Vapi dashboard schema:
    // {
    //   "name": "check-appointment-availability",
    //   "description": "Verifica se uno slot è disponibile per una data e ora specifiche. Chiamare PRIMA di book-appointment.",
    //   "parameters": {
    //     "type": "object",
    //     "properties": {
    //       "serviceTypeCode": { "type": "string", "enum": ["ANAGRAFE","TRIBUTI","URP","SERVIZI_SOCIALI"] },
    //       "appointmentDate": { "type": "string", "format": "date", "description": "yyyy-MM-dd" },
    //       "appointmentTime": { "type": "string", "description": "HH:mm" }
    //     },
    //     "required": ["serviceTypeCode","appointmentDate","appointmentTime"]
    //   },
    //   "server": { "url": "https://YOUR_HOST/api/vapi/tools/check-appointment-availability" }
    // }
    // =========================================================================

    /**
     * Handles the "check-appointment-availability" tool call.
     * A slot is available when no BOOKED appointment exists for that service + date + time.
     */
    public VapiToolCallResponse handleCheckAvailability(String toolCallId, JsonNode argumentsNode) {
        Map<String, Object> args;
        try {
            args = parseArgsAsMap(argumentsNode);
        } catch (Exception e) {
            log.warn("[{}] check-availability: failed to parse arguments – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile verificare la disponibilità: parametri non validi.");
        }

        String serviceTypeCode = (String) args.get("serviceTypeCode");
        String appointmentDate  = (String) args.get("appointmentDate");
        String appointmentTime  = (String) args.get("appointmentTime");

        if (isBlank(serviceTypeCode) || isBlank(appointmentDate) || isBlank(appointmentTime)) {
            log.warn("[{}] check-availability: missing required fields", toolCallId);
            return VapiToolCallResponse.of(toolCallId,
                "Per verificare la disponibilità sono necessari: tipo di servizio, data e ora.");
        }

        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(appointmentDate);
            time = LocalTime.parse(appointmentTime);
        } catch (DateTimeParseException e) {
            log.warn("[{}] check-availability: invalid date/time – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Formato data o ora non valido. Usare yyyy-MM-dd per la data e HH:mm per l'ora.");
        }

        if (!date.isAfter(LocalDate.now())) {
            return VapiToolCallResponse.of(toolCallId,
                "Non è possibile verificare la disponibilità per una data passata o per oggi.");
        }

        log.info("[{}] check-availability – service={}, date={}, time={}",
            toolCallId, serviceTypeCode, date, time);

        boolean available;
        try {
            available = appointmentService.isSlotAvailable(serviceTypeCode, date, time);
        } catch (Exception e) {
            log.error("[{}] check-availability error: {}", toolCallId, e.getMessage(), e);
            return VapiToolCallResponse.of(toolCallId,
                "Si è verificato un errore durante la verifica della disponibilità. Riprova più tardi.");
        }

        String formattedDate = date.format(ITALIAN_DATE);
        if (available) {
            return VapiToolCallResponse.of(toolCallId, String.format(
                "Lo slot del %s alle %s per il servizio %s è disponibile. Puoi procedere con la prenotazione.",
                formattedDate, time, serviceTypeCode
            ));
        } else {
            return VapiToolCallResponse.of(toolCallId, String.format(
                "Lo slot del %s alle %s per il servizio %s non è disponibile: è già prenotato. " +
                "Chiedi al cittadino un'altra data o un altro orario.",
                formattedDate, time, serviceTypeCode
            ));
        }
    }

    // =========================================================================
    // Tool: get-appointments-by-name
    //
    // Vapi dashboard schema:
    // {
    //   "name": "get-appointments-by-name",
    //   "description": "Recupera gli appuntamenti attivi di un cittadino dato il suo nome e cognome.",
    //   "parameters": {
    //     "type": "object",
    //     "properties": {
    //       "citizenName": { "type": "string", "description": "Nome e/o cognome (ricerca parziale case-insensitive)" }
    //     },
    //     "required": ["citizenName"]
    //   },
    //   "server": { "url": "https://YOUR_HOST/api/vapi/tools/get-appointments-by-name" }
    // }
    // =========================================================================

    /**
     * Handles the "get-appointments-by-name" tool call.
     * Partial case-insensitive name match: works when the citizen gives only their surname.
     */
    public VapiToolCallResponse handleGetAppointmentsByName(String toolCallId, JsonNode argumentsNode) {
        Map<String, Object> args;
        try {
            args = parseArgsAsMap(argumentsNode);
        } catch (Exception e) {
            log.warn("[{}] get-appointments-by-name: failed to parse arguments – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile cercare gli appuntamenti: parametri non validi.");
        }

        String citizenName = (String) args.get("citizenName");
        if (isBlank(citizenName)) {
            log.warn("[{}] get-appointments-by-name: missing citizenName", toolCallId);
            return VapiToolCallResponse.of(toolCallId,
                "Per cercare gli appuntamenti è necessario il nome del cittadino.");
        }

        log.info("[{}] get-appointments-by-name – name='{}'", toolCallId, citizenName);

        List<Appointment> appointments;
        try {
            // findByCitizenName already excludes deleted rows;
            // additionally filter out CANCELLED so only BOOKED appointments are shown.
            appointments = appointmentService.findByCitizenName(citizenName).stream()
                .filter(a -> a.getStatus() == com.impruneta.vapiagent.appointment.AppointmentStatus.BOOKED)
                .toList();
        } catch (Exception e) {
            log.error("[{}] get-appointments-by-name error: {}", toolCallId, e.getMessage(), e);
            return VapiToolCallResponse.of(toolCallId,
                "Si è verificato un errore durante la ricerca degli appuntamenti. Riprova più tardi.");
        }

        if (appointments.isEmpty()) {
            return VapiToolCallResponse.of(toolCallId, String.format(
                "Non ho trovato appuntamenti attivi per '%s'.", citizenName
            ));
        }

        return VapiToolCallResponse.of(toolCallId, formatAppointmentList(appointments));
    }

    private String formatAppointmentList(List<Appointment> appointments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ho trovato i seguenti appuntamenti:\n\n");
        for (Appointment a : appointments) {
            sb.append(String.format(
                "- %s: %s alle %s (servizio: %s)",
                a.getCitizenName(),
                a.getAppointmentDate().format(ITALIAN_DATE),
                a.getAppointmentTime().toString(),
                a.getServiceTypeCode()
            ));
            if (a.getNotes() != null && !a.getNotes().isBlank()) {
                sb.append(" — note: ").append(a.getNotes());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // =========================================================================
    // Tool: cancel-appointment
    //
    // Vapi dashboard schema:
    // {
    //   "name": "cancel-appointment",
    //   "description": "Cancella un appuntamento attivo. Richiede email, tipo di servizio e data.",
    //   "parameters": {
    //     "type": "object",
    //     "properties": {
    //       "citizenEmail":    { "type": "string", "format": "email" },
    //       "serviceTypeCode": { "type": "string", "enum": ["ANAGRAFE","TRIBUTI","URP","SERVIZI_SOCIALI"] },
    //       "appointmentDate": { "type": "string", "format": "date", "description": "yyyy-MM-dd" }
    //     },
    //     "required": ["citizenEmail","serviceTypeCode","appointmentDate"]
    //   },
    //   "server": { "url": "https://YOUR_HOST/api/vapi/tools/cancel-appointment" }
    // }
    //
    // The citizen is not expected to know their UUID.
    // email + date + serviceTypeCode uniquely identifies the appointment.
    // =========================================================================

    /**
     * Handles the "cancel-appointment" tool call.
     * Voice-friendly: no UUID required from the citizen.
     */
    public VapiToolCallResponse handleCancelAppointment(String toolCallId, JsonNode argumentsNode) {
        Map<String, Object> args;
        try {
            args = parseArgsAsMap(argumentsNode);
        } catch (Exception e) {
            log.warn("[{}] cancel-appointment: failed to parse arguments – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Non è stato possibile elaborare la cancellazione: parametri non validi.");
        }

        String citizenEmail    = (String) args.get("citizenEmail");
        String serviceTypeCode = (String) args.get("serviceTypeCode");
        String appointmentDate = (String) args.get("appointmentDate");

        if (isBlank(citizenEmail) || isBlank(serviceTypeCode) || isBlank(appointmentDate)) {
            log.warn("[{}] cancel-appointment: missing required fields", toolCallId);
            return VapiToolCallResponse.of(toolCallId,
                "Per cancellare l'appuntamento sono necessari: email, tipo di servizio e data.");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(appointmentDate);
        } catch (DateTimeParseException e) {
            log.warn("[{}] cancel-appointment: invalid date – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Formato data non valido. Usare yyyy-MM-dd (es. 2026-07-15).");
        }

        log.info("[{}] cancel-appointment – email={}, service={}, date={}",
            toolCallId, citizenEmail, serviceTypeCode, date);

        Appointment cancelled;
        try {
            cancelled = appointmentService.cancelBySlot(citizenEmail, date, serviceTypeCode);
        } catch (IllegalArgumentException e) {
            log.warn("[{}] cancel-appointment: not found – {}", toolCallId, e.getMessage());
            return VapiToolCallResponse.of(toolCallId,
                "Nessun appuntamento attivo trovato con i dati forniti. " +
                "Verificare email, data e tipo di servizio.");
        } catch (Exception e) {
            log.error("[{}] cancel-appointment unexpected error: {}", toolCallId, e.getMessage(), e);
            return VapiToolCallResponse.of(toolCallId,
                "Si è verificato un errore durante la cancellazione. Riprova più tardi.");
        }

        return VapiToolCallResponse.of(toolCallId, String.format(
            "Appuntamento del %s alle %s per il servizio %s cancellato con successo.",
            cancelled.getAppointmentDate().format(ITALIAN_DATE),
            cancelled.getAppointmentTime().toString(),
            cancelled.getServiceTypeCode()
        ));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Normalises tool arguments from either Vapi production format (JSON-encoded String)
     * or test/curl format (plain JSON Object) into a Map&lt;String, Object&gt;.
     *
     * Vapi production sends: {@code "arguments": "{\"query\":\"...\"}"}  (textual node)
     * Manual testing sends:  {@code "arguments": {"query":"..."}}          (object node)
     */
    private Map<String, Object> parseArgsAsMap(JsonNode node) throws Exception {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("arguments is null");
        }
        if (node.isTextual()) {
            // Vapi production: arguments is a JSON-encoded string
            return objectMapper.readValue(node.asText(), new TypeReference<>() {});
        }
        // Test/curl: arguments is already a JSON object
        return objectMapper.convertValue(node, new TypeReference<>() {});
    }

    /**
     * Same normalisation as parseArgsAsMap, but deserialises directly into a typed class.
     * Used for book-appointment where a dedicated record provides self-documenting field names.
     */
    private <T> T parseArgs(JsonNode node, Class<T> type) throws Exception {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("arguments is null");
        }
        if (node.isTextual()) {
            return objectMapper.readValue(node.asText(), type);
        }
        return objectMapper.treeToValue(node, type);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Internal DTO for deserialising book-appointment arguments.
     *
     * Using a dedicated record instead of Map<String, Object> makes the expected fields
     * self-documenting and produces a clear error message when a field is wrongly typed.
     * Scope is private to this service — not part of the package API.
     */
    private record BookAppointmentArgs(
        String citizenName,
        String citizenEmail,
        String serviceTypeCode,
        String appointmentDate,
        String appointmentTime,
        String notes
    ) {
    }
}
