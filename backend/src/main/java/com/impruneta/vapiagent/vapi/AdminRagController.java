package com.impruneta.vapiagent.vapi;

import com.impruneta.vapiagent.common.JobResult;
import com.impruneta.vapiagent.rag.chunking.ChunkingService;
import com.impruneta.vapiagent.rag.embedding.EmbeddingOrchestrationService;
import com.impruneta.vapiagent.rag.ingestion.IngestionService;
import com.impruneta.vapiagent.rag.retrieval.RetrievalResult;
import com.impruneta.vapiagent.rag.retrieval.RetrievalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin REST endpoints for the RAG pipeline.
 *
 * All endpoints are synchronous and blocking. They are designed for:
 *   - Manual pipeline invocation during development
 *   - Integration with a CI/CD data refresh script
 *   - Smoke testing during interviews
 *
 * Typical pipeline execution order:
 *   1. POST /admin/rag/ingest  – scrape seed URLs → populate municipal_document
 *   2. POST /admin/rag/chunk   – chunk all documents → populate municipal_document_chunk
 *   3. POST /admin/rag/embed   – generate embeddings → populate chunk.embedding vectors
 *   4. GET  /admin/rag/retrieve?query=... – test semantic search
 *
 * Security note:
 *   In production these endpoints must be protected (e.g. Spring Security with ROLE_ADMIN).
 *   They are left unprotected here to keep the assignment scope minimal.
 *
 * Package placement:
 *   This lives in the 'vapi' package because in the full application this package will
 *   also contain the Vapi.ai webhook handler (tool call dispatcher, function resolvers).
 */
@RestController
@RequestMapping("/admin/rag")
public class AdminRagController {

    private final IngestionService ingestionService;
    private final ChunkingService chunkingService;
    private final EmbeddingOrchestrationService embeddingOrchestrationService;
    private final RetrievalService retrievalService;

    public AdminRagController(IngestionService ingestionService,
                               ChunkingService chunkingService,
                               EmbeddingOrchestrationService embeddingOrchestrationService,
                               RetrievalService retrievalService) {
        this.ingestionService = ingestionService;
        this.chunkingService = chunkingService;
        this.embeddingOrchestrationService = embeddingOrchestrationService;
        this.retrievalService = retrievalService;
    }

    /**
     * Scrapes all enabled seed URLs and upserts MunicipalDocument rows.
     * Unchanged pages (same checksum) are skipped.
     *
     * @return { processed, skipped, failed }
     */
    @PostMapping("/ingest")
    public ResponseEntity<JobResult> ingest() {
        return ResponseEntity.ok(ingestionService.ingestAll());
    }

    /**
     * Chunks all MunicipalDocuments in the database.
     * Existing chunks for each document are deleted and recreated.
     * After running this, embeddings must be regenerated (run /embed).
     *
     * @return { processed, skipped, failed } – skipped is always 0 (all docs are re-chunked)
     */
    @PostMapping("/chunk")
    public ResponseEntity<JobResult> chunk() {
        return ResponseEntity.ok(chunkingService.chunkAll());
    }

    /**
     * Generates OpenAI embeddings for all chunks where embedding IS NULL.
     * Idempotent: already-embedded chunks are not touched.
     * Failed chunks keep their NULL embedding and will be retried on the next call.
     *
     * Requires the OPENAI_API_KEY environment variable to be set.
     *
     * @return { processed, skipped, failed }
     */
    @PostMapping("/embed")
    public ResponseEntity<JobResult> embed() {
        return ResponseEntity.ok(embeddingOrchestrationService.embedAll());
    }

    /**
     * Performs a semantic similarity search over embedded chunks.
     * Useful for testing retrieval quality during development.
     *
     * @param query free-text query (e.g. "Come faccio a richiedere la residenza?")
     * @param topK  number of results to return (default: 5)
     * @return list of RetrievalResult ordered by descending similarity score
     */
    @GetMapping("/retrieve")
    public ResponseEntity<List<RetrievalResult>> retrieve(
        @RequestParam String query,
        @RequestParam(defaultValue = "5") int topK
    ) {
        return ResponseEntity.ok(retrievalService.retrieveMunicipalInfo(query, topK));
    }
}
