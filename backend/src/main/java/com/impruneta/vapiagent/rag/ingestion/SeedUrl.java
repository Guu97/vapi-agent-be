package com.impruneta.vapiagent.rag.ingestion;

/**
 * Represents a single URL to scrape during the ingestion pipeline.
 *
 * @param url         full URL to fetch and index
 * @param serviceType maps to service_type.code in the DB (e.g. "ANAGRAFE", "URP")
 * @param enabled     when false, this seed is listed but skipped during ingestion;
 *                    useful to temporarily disable a URL without removing it
 */
public record SeedUrl(String url, String serviceType, boolean enabled) {
}
