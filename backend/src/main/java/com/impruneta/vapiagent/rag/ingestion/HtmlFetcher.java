package com.impruneta.vapiagent.rag.ingestion;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fetches a URL and returns a parsed Jsoup Document.
 *
 * Applies a configurable User-Agent and timeout to avoid server blocks and hung connections.
 * Follows redirects automatically (many Italian municipal sites redirect http → https).
 *
 * Throws IOException on network errors, HTTP 4xx/5xx responses, or timeout.
 * The caller (IngestionService) is responsible for catching and logging these.
 */
@Component
public class HtmlFetcher {

    private static final Logger log = LoggerFactory.getLogger(HtmlFetcher.class);

    private final String userAgent;
    private final int timeoutMs;

    public HtmlFetcher(
        @Value("${app.rag.ingestion.user-agent}") String userAgent,
        @Value("${app.rag.ingestion.timeout-ms}") int timeoutMs
    ) {
        this.userAgent = userAgent;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Fetches the HTML at the given URL and returns a parsed Jsoup Document.
     *
     * @param url absolute URL to fetch
     * @return parsed document
     * @throws IOException if the connection fails, times out, or the server returns an error status
     */
    public Document fetch(String url) throws IOException {
        log.debug("Fetching: {}", url);
        Document doc = Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(timeoutMs)
            .followRedirects(true)
            .get();
        log.debug("Fetched {} chars from {}", doc.html().length(), url);
        return doc;
    }
}
