package com.impruneta.vapiagent.calllog;

import com.impruneta.vapiagent.calllog.dto.CallLogResponse;
import com.impruneta.vapiagent.calllog.dto.VapiEndOfCallReportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Handles persistence and retrieval of Vapi end-of-call logs.
 *
 * Responsibilities:
 *   - Map the Vapi webhook DTO to a CallLog entity and persist it
 *   - Skip duplicate inserts (same vapi_call_id) without crashing
 *   - Return recent logs as response DTOs for the frontend dashboard
 */
@Service
public class CallLogService {

    private static final Logger log = LoggerFactory.getLogger(CallLogService.class);

    private final CallLogRepository callLogRepository;

    public CallLogService(CallLogRepository callLogRepository) {
        this.callLogRepository = callLogRepository;
    }

    /**
     * Persists the end-of-call report from the Vapi webhook.
     *
     * If a log already exists for the same vapi_call_id, the insert is skipped and
     * the existing record is returned without any error.
     */
    @Transactional
    public void saveEndOfCallReport(VapiEndOfCallReportRequest.VapiEndOfCallMessage message) {
        String callId = message.call() != null ? message.call().id() : null;

        if (callId == null) {
            log.warn("Received end-of-call-report with no call.id — skipping");
            return;
        }

        boolean alreadyExists = callLogRepository.findByVapiCallId(callId).isPresent();
        if (alreadyExists) {
            log.info("CallLog already exists for vapi_call_id={} — skipping duplicate", callId);
            return;
        }

        CallLog callLog = new CallLog();
        callLog.setId(UUID.randomUUID());
        callLog.setVapiCallId(callId);
        callLog.setTranscript(message.transcript() != null ? message.transcript() : "");
        callLog.setSummary(message.summary());
        callLog.setDurationSeconds(message.durationSeconds());
        callLog.setStartedAt(message.call().startedAt());
        callLog.setEndedAt(message.call().endedAt());
        callLog.setCreatedAt(OffsetDateTime.now());

        try {
            callLogRepository.save(callLog);
            log.info("Saved CallLog for vapi_call_id={}", callId);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another request already inserted the same call id
            log.warn("Duplicate insert for vapi_call_id={} — ignoring", callId);
        }
    }

    /**
     * Returns the most recent call logs for the frontend dashboard.
     *
     * @param limit maximum number of results to return
     */
    @Transactional(readOnly = true)
    public List<CallLogResponse> getRecentLogs(int limit) {
        return callLogRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CallLogResponse toResponse(CallLog callLog) {
        return new CallLogResponse(
                callLog.getId(),
                callLog.getVapiCallId(),
                callLog.getSummary(),
                callLog.getTranscript(),
                callLog.getDurationSeconds(),
                callLog.getStartedAt(),
                callLog.getEndedAt(),
                callLog.getCreatedAt()
        );
    }
}
