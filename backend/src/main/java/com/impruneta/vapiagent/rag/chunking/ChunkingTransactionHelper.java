package com.impruneta.vapiagent.rag.chunking;

import com.impruneta.vapiagent.municipaldocument.MunicipalDocument;
import com.impruneta.vapiagent.municipaldocument.MunicipalDocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.impruneta.vapiagent.municipaldocument.MunicipalDocumentChunkRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles the transactional delete-then-insert for a single document's chunks.
 *
 * This is a separate Spring bean (not an inner method of ChunkingService) specifically
 * to enable REQUIRES_NEW transaction propagation. This gives each document its own
 * independent transaction: if one document's chunking fails, only that document's
 * transaction rolls back and the others are not affected.
 *
 * Why not just @Transactional on the outer chunkAll() loop?
 *   If chunkAll() is @Transactional and an exception is caught inside the loop, the
 *   JPA EntityManager may end up in a rollback-only state, causing the entire batch
 *   to fail when the outer transaction tries to commit. REQUIRES_NEW avoids this.
 */
@Component
class ChunkingTransactionHelper {

    private final MunicipalDocumentChunkRepository chunkRepository;

    ChunkingTransactionHelper(MunicipalDocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    /**
     * Deletes all existing chunks for the document and replaces them with new ones.
     * Runs in its own independent transaction (REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replaceChunks(MunicipalDocument doc, List<String> chunkTexts) {
        chunkRepository.deleteByDocumentId(doc.getId());

        List<MunicipalDocumentChunk> newChunks = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            MunicipalDocumentChunk chunk = new MunicipalDocumentChunk();
            chunk.setId(UUID.randomUUID());
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setTitle(doc.getTitle());
            chunk.setServiceType(doc.getServiceType());
            chunk.setContent(chunkTexts.get(i));
            chunk.setSourceUrl(doc.getSourceUrl());
            chunk.setCreatedAt(OffsetDateTime.now());
            newChunks.add(chunk);
        }
        chunkRepository.saveAll(newChunks);
    }
}
