package com.impruneta.vapiagent.common;

/**
 * Returned by every RAG batch job (ingestion, chunking, embedding).
 *
 * @param processed items that were successfully created or updated
 * @param skipped   items that were intentionally skipped (e.g. unchanged checksum)
 * @param failed    items that encountered an error; details are in the application log
 */
public record JobResult(int processed, int skipped, int failed) {

    public static JobResult empty() {
        return new JobResult(0, 0, 0);
    }
}
