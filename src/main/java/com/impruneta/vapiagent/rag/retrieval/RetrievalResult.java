package com.impruneta.vapiagent.rag.retrieval;

import java.util.UUID;

/**
 * A single result from a semantic similarity search over document chunks.
 *
 * @param chunkId     UUID of the matching municipal_document_chunk row
 * @param documentId  UUID of the parent municipal_document
 * @param content     the chunk text – this is the context passed to the LLM
 * @param sourceUrl   origin URL, useful for attribution in responses
 * @param title       document title, provides framing context
 * @param serviceType e.g. "ANAGRAFE", "TRIBUTI" – can be used for filtering or routing
 * @param score       cosine similarity score in [0.0, 1.0]; higher means more relevant
 */
public record RetrievalResult(
    UUID chunkId,
    UUID documentId,
    String content,
    String sourceUrl,
    String title,
    String serviceType,
    double score
) {
}
