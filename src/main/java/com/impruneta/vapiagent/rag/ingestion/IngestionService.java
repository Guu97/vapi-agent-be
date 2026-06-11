package com.impruneta.vapiagent.rag.ingestion;

import com.impruneta.vapiagent.common.ChecksumUtil;
import com.impruneta.vapiagent.common.JobResult;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocument;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the web scraping ingestion pipeline.
 *
 * For each enabled seed URL:
 *   1. Fetch the HTML page via Jsoup (HtmlFetcher)
 *   2. Extract title + clean paragraph-separated text (HtmlExtractor)
 *   3. Compute SHA-256 checksum of the extracted text
 *   4a. If the URL has never been seen: INSERT a new MunicipalDocument
 *   4b. If the URL exists and checksum changed: UPDATE title, text, html, checksum
 *   4c. If the URL exists and checksum is unchanged: SKIP (no DB write needed)
 *
 * Error handling:
 *   One failing URL never aborts the batch. The exception is caught, logged at WARN,
 *   and the URL is counted as failed. All other seeds continue normally.
 *
 * Note: after ingestion, re-chunking and re-embedding are required for updated documents.
 * The chunking step detects this by always re-processing all documents.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final SeedRegistry seedRegistry;
    private final HtmlFetcher htmlFetcher;
    private final HtmlExtractor htmlExtractor;
    private final MunicipalDocumentRepository documentRepository;

    public IngestionService(SeedRegistry seedRegistry,
                             HtmlFetcher htmlFetcher,
                             HtmlExtractor htmlExtractor,
                             MunicipalDocumentRepository documentRepository) {
        this.seedRegistry = seedRegistry;
        this.htmlFetcher = htmlFetcher;
        this.htmlExtractor = htmlExtractor;
        this.documentRepository = documentRepository;
    }

    /**
     * Runs the full ingestion batch over all enabled seeds.
     *
     * @return JobResult with counts of processed (new/updated), skipped (unchanged), failed
     */
    @Transactional
    public JobResult ingestAll() {
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (SeedUrl seed : seedRegistry.getEnabledSeeds()) {
            try {
                IngestOutcome outcome = ingestOne(seed);
                switch (outcome) {
                    case CREATED, UPDATED -> processed++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                log.warn("Failed to ingest '{}': {}", seed.url(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Ingestion complete – processed={}, skipped={}, failed={}", processed, skipped, failed);
        return new JobResult(processed, skipped, failed);
    }

    private IngestOutcome ingestOne(SeedUrl seed) throws Exception {
        var htmlDoc = htmlFetcher.fetch(seed.url());
        var extracted = htmlExtractor.extract(htmlDoc);
        String checksum = ChecksumUtil.sha256(extracted.text());

        Optional<MunicipalDocument> existing = documentRepository.findBySourceUrl(seed.url());

        if (existing.isPresent()) {
            MunicipalDocument doc = existing.get();
            if (checksum.equals(doc.getChecksum())) {
                log.debug("Unchanged (skipping): {}", seed.url());
                return IngestOutcome.SKIPPED;
            }
            // Content has changed – update and mark for re-chunking + re-embedding
            doc.setTitle(extracted.title());
            doc.setRawHtml(extracted.rawHtml());
            doc.setRawText(extracted.text());
            doc.setChecksum(checksum);
            doc.setUpdatedAt(OffsetDateTime.now());
            documentRepository.save(doc);
            log.info("Updated: {}", seed.url());
            return IngestOutcome.UPDATED;
        }

        // New document
        MunicipalDocument doc = new MunicipalDocument();
        doc.setId(UUID.randomUUID());
        doc.setSourceUrl(seed.url());
        doc.setTitle(extracted.title());
        doc.setServiceType(seed.serviceType());
        doc.setRawHtml(extracted.rawHtml());
        doc.setRawText(extracted.text());
        doc.setChecksum(checksum);
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(doc);
        log.info("Created: {}", seed.url());
        return IngestOutcome.CREATED;
    }

    private enum IngestOutcome {
        CREATED, UPDATED, SKIPPED
    }
}
