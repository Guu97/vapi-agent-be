package com.impruneta.vapiagent.rag.chunking;

import com.impruneta.vapiagent.common.JobResult;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocument;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits each MunicipalDocument's raw text into fixed-size chunks.
 *
 * Algorithm (paragraph-based, no overlap in v1):
 *
 *   1. Normalise the text: unify line endings, collapse 3+ consecutive blank lines to 2.
 *   2. Split on double newline ("\n\n") to get individual paragraphs.
 *      (HtmlExtractor already inserts "\n\n" between block elements when building raw_text.)
 *   3. Discard blank paragraphs.
 *   4. Accumulate paragraphs into a buffer until the buffer length reaches targetChunkSize.
 *      When the threshold is reached AND the current buffer is already >= minChunkSize,
 *      flush the buffer as a new chunk and start a fresh one.
 *      Short paragraphs are always appended rather than standing alone.
 *   5. Flush any remaining buffer as the final chunk.
 *   6. If splitting produces no chunks (e.g. very short page), the whole text is one chunk.
 *
 * Transaction strategy:
 *   ChunkingTransactionHelper.replaceChunks() runs in REQUIRES_NEW, giving each document
 *   its own transaction. A failure on one document does not affect the others.
 *
 * Important: re-running this job will delete and recreate all chunks.
 *   Previously computed embeddings will be lost. The embedding job must be re-run afterwards.
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private final MunicipalDocumentRepository documentRepository;
    private final ChunkingProperties properties;
    private final ChunkingTransactionHelper transactionHelper;

    public ChunkingService(MunicipalDocumentRepository documentRepository,
                            ChunkingProperties properties,
                            ChunkingTransactionHelper transactionHelper) {
        this.documentRepository = documentRepository;
        this.properties = properties;
        this.transactionHelper = transactionHelper;
    }

    /**
     * Chunks all documents currently in the database.
     *
     * @return JobResult with counts of processed and failed documents
     */
    public JobResult chunkAll() {
        int processed = 0;
        int failed = 0;

        List<MunicipalDocument> documents = documentRepository.findAll();
        log.info("Starting chunking for {} documents", documents.size());

        for (MunicipalDocument doc : documents) {
            try {
                List<String> chunks = splitIntoChunks(doc.getRawText());
                transactionHelper.replaceChunks(doc, chunks);
                log.info("Chunked document {} into {} chunks: {}", doc.getId(), chunks.size(), doc.getSourceUrl());
                processed++;
            } catch (Exception e) {
                log.warn("Failed to chunk document {} ({}): {}", doc.getId(), doc.getSourceUrl(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("Chunking complete – processed={}, failed={}", processed, failed);
        return new JobResult(processed, 0, failed);
    }

    /**
     * Splits raw text into a list of chunk strings using the paragraph accumulation algorithm.
     * Package-visible for unit testing.
     */
    List<String> splitIntoChunks(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        // Step 1: normalise line endings and collapse excessive blank lines
        String normalised = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replaceAll("\n{3,}", "\n\n")
            .trim();

        // Step 2: split on paragraph boundaries
        String[] paragraphs = normalised.split("\n\n");

        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String para = paragraph.trim();
            if (para.isBlank()) {
                continue;
            }

            // Buffer is empty – always start accumulating
            if (buffer.isEmpty()) {
                buffer.append(para);
                continue;
            }

            int projectedLength = buffer.length() + 2 + para.length(); // 2 for "\n\n"

            if (projectedLength >= properties.getTargetChunkSize()
                    && buffer.length() >= properties.getMinChunkSize()) {
                // Flush the current buffer and start a new one
                chunks.add(buffer.toString());
                buffer = new StringBuilder(para);
            } else {
                // Accumulate: append with paragraph separator
                buffer.append("\n\n").append(para);
            }
        }

        // Flush remaining content
        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString());
        }

        // Guarantee at least one chunk
        if (chunks.isEmpty()) {
            chunks.add(rawText.trim());
        }

        return chunks;
    }
}
