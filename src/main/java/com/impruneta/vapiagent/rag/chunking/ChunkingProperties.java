package com.impruneta.vapiagent.rag.chunking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning parameters for the paragraph-based chunking algorithm.
 * Bound from application.yml under the "app.rag.chunking" prefix.
 *
 * Registered as a Spring bean by @ConfigurationPropertiesScan on VapiAgentBeApplication.
 */
@ConfigurationProperties(prefix = "app.rag.chunking")
public class ChunkingProperties {

    /**
     * Approximate target character count per chunk.
     * The chunker accumulates paragraphs until the buffer reaches this length,
     * then flushes it as a new chunk.
     * Default: 800 characters (~120–150 tokens, well within embedding model limits).
     */
    private int targetChunkSize = 800;

    /**
     * Paragraphs shorter than this (in characters) are appended to the current
     * accumulation buffer instead of triggering a flush.
     * This prevents standalone one-line chunks that carry little context.
     * Default: 200 characters.
     */
    private int minChunkSize = 200;

    public int getTargetChunkSize() { return targetChunkSize; }
    public void setTargetChunkSize(int targetChunkSize) { this.targetChunkSize = targetChunkSize; }

    public int getMinChunkSize() { return minChunkSize; }
    public void setMinChunkSize(int minChunkSize) { this.minChunkSize = minChunkSize; }
}
