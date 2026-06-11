package com.impruneta.vapiagent.rag.embedding;

import com.impruneta.vapiagent.common.JobResult;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocumentChunk;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Finds all chunks that lack an embedding and generates vectors for them.
 *
 * Strategy:
 *   1. Load all chunks where embedding IS NULL in a single query.
 *      For this dataset (10 pages → ~50–100 chunks) loading all at once is safe.
 *      In a larger system, batch with LIMIT/OFFSET and track progress.
 *   2. For each chunk, call EmbeddingService.embed(content) to get a float[768].
 *   3. Persist the vector immediately via EmbeddingVectorRepository (JdbcTemplate).
 *   4. One failing chunk never stops the batch – it is counted as failed, logged,
 *      and will be retried on the next /admin/rag/embed call (its embedding stays NULL).
 *
 * Note: this service has no @Transactional annotation on embedAll().
 *   Each updateEmbedding() call is an independent UPDATE – there is no need to wrap
 *   the entire batch in one transaction, and doing so would hold a DB connection for
 *   the full duration of all OpenAI API calls (potentially several minutes).
 */
@Service
public class EmbeddingOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingOrchestrationService.class);

    private final MunicipalDocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final EmbeddingVectorRepository embeddingVectorRepository;

    public EmbeddingOrchestrationService(MunicipalDocumentChunkRepository chunkRepository,
                                          EmbeddingService embeddingService,
                                          EmbeddingVectorRepository embeddingVectorRepository) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.embeddingVectorRepository = embeddingVectorRepository;
    }

    /**
     * Runs the embedding batch over all chunks that currently have a NULL embedding.
     *
     * @return JobResult with counts of embedded (processed) and failed chunks
     */
    public JobResult embedAll() {
        List<MunicipalDocumentChunk> pending = chunkRepository.findAllWithNullEmbedding();
        log.info("Found {} chunks pending embedding", pending.size());

        if (pending.isEmpty()) {
            return JobResult.empty();
        }

        int processed = 0;
        int failed = 0;

        for (MunicipalDocumentChunk chunk : pending) {
            try {
                float[] embedding = embeddingService.embed(chunk.getContent());
                embeddingVectorRepository.updateEmbedding(chunk.getId(), embedding);
                processed++;

                if (processed % 10 == 0) {
                    log.info("Embedded {}/{} chunks...", processed, pending.size());
                }
            } catch (Exception e) {
                // This chunk's embedding stays NULL and will be retried on the next run
                log.warn("Failed to embed chunk {} (doc={}): {}",
                    chunk.getId(), chunk.getDocumentId(), e.getMessage());
                failed++;
            }
        }

        log.info("Embedding complete – processed={}, failed={}", processed, failed);
        if (failed > 0) {
            log.warn("{} chunks failed embedding and will be retried on the next /admin/rag/embed call", failed);
        }

        return new JobResult(processed, 0, failed);
    }
}
