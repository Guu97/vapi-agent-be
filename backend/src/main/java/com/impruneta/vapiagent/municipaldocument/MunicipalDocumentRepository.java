package com.impruneta.vapiagent.municipaldocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MunicipalDocumentRepository extends JpaRepository<MunicipalDocument, UUID> {

    /**
     * Used during ingestion to check whether a URL has already been scraped.
     * The source_url column has a UNIQUE constraint in the DB.
     */
    Optional<MunicipalDocument> findBySourceUrl(String sourceUrl);
}
