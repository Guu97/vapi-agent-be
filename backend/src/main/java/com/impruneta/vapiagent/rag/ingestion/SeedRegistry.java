package com.impruneta.vapiagent.rag.ingestion;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hardcoded list of municipal website pages to scrape.
 *
 * Service type mapping (based on content category):
 *
 *   URP             → homepage, general services index, office hours, segnalazioni
 *   ANAGRAFE        → anagrafe e stato civile, anagrafe organisational unit
 *   TRIBUTI         → tributi unit, TARI page, pratiche tributi service
 *   SERVIZI_SOCIALI → servizi sociali e sanità
 *
 * To disable a URL temporarily without removing it, set enabled=false.
 * To add new URLs, add a new SeedUrl entry and re-run the ingestion job.
 */
@Component
public class SeedRegistry {

    private static final List<SeedUrl> SEEDS = List.of(
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it",
            "URP", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/menu/servizi",
            "URP", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/page/anagrafe-e-stato-civile",
            "ANAGRAFE", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/unita_organizzative/anagrafe",
            "ANAGRAFE", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/page/orario-degli-uffici-comunali",
            "URP", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/unita_organizzative/tributi",
            "TRIBUTI", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/page/tari-tassa-rifiuti-e-servizi",
            "TRIBUTI", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/servizi/pratiche-riguardanti-tributi-comunali",
            "TRIBUTI", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/page/segnalazioni",
            "URP", true
        ),
        new SeedUrl(
            "https://www.comune.impruneta.fi.it/it/page/servizi-sociali-e-sanita",
            "SERVIZI_SOCIALI", true
        )
    );

    /**
     * Returns only the seeds that have enabled=true.
     * Called by IngestionService to drive the scraping loop.
     */
    public List<SeedUrl> getEnabledSeeds() {
        return SEEDS.stream()
            .filter(SeedUrl::enabled)
            .toList();
    }

    /**
     * Returns all seeds regardless of enabled status.
     * Useful for inspection and debugging.
     */
    public List<SeedUrl> getAllSeeds() {
        return SEEDS;
    }
}
