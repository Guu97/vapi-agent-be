package com.impruneta.vapiagent.calllog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallLogRepository extends JpaRepository<CallLog, UUID> {

    /**
     * Returns recent call logs ordered by createdAt descending.
     * Pass {@code PageRequest.of(0, limit)} from the service layer to cap the result size.
     */
    List<CallLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<CallLog> findByVapiCallId(String vapiCallId);
}
