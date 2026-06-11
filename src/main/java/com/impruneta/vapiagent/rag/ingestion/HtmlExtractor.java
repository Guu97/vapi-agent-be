package com.impruneta.vapiagent.rag.ingestion;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts a structured title and clean paragraph-separated text from a Jsoup Document.
 *
 * Extraction strategy:
 *
 *   1. Clone the document and remove boilerplate: nav, header, footer, scripts,
 *      cookie banners, sidebars, breadcrumbs.
 *
 *   2. Title extraction (in priority order):
 *      a. First non-blank <h1> text
 *      b. <title> tag text
 *      c. Fallback: "Untitled"
 *
 *   3. Content extraction – try CSS selectors in priority order:
 *      "article" → "main" → "[role=main]" → ".field-item" → ".view-content"
 *      → "#main-content" → ".region-content" → "body"
 *      A candidate is accepted when its extracted text is > 100 characters.
 *
 *   4. Text is built by iterating block-level elements (p, h1-h5, li) inside the
 *      chosen content element and joining their text with "\n\n".
 *      This preserves paragraph boundaries needed by ChunkingService.
 *
 * The rawHtml in ExtractionResult is the full unmodified page HTML, stored for
 * archival purposes.
 */
@Component
public class HtmlExtractor {

    private static final Logger log = LoggerFactory.getLogger(HtmlExtractor.class);

    /**
     * Holds the data extracted from a single scraped page.
     *
     * @param title   page title
     * @param text    cleaned plain text with "\n\n" between paragraphs
     * @param rawHtml full outer HTML of the original document (unmodified)
     */
    public record ExtractionResult(String title, String text, String rawHtml) {
    }

    /** CSS selectors tried in order to locate the main content area. */
    private static final String[] CONTENT_SELECTORS = {
        "article",
        "main",
        "[role=main]",
        ".field-item",
        ".view-content",
        "#main-content",
        ".region-content",
        "body"
    };

    /** Elements whose text is purely navigational boilerplate – removed before extraction. */
    private static final String[] NOISE_SELECTORS = {
        "nav", "header", "footer", "script", "style", "noscript",
        ".nav", ".navbar", ".header", ".footer",
        ".menu", ".breadcrumb", ".pagination",
        ".sidebar", ".widget",
        "#cookie-banner", "#cookiebar",
        "iframe", "form"
    };

    public ExtractionResult extract(Document doc) {
        String rawHtml = doc.outerHtml();

        // Work on a clone so the original document is not mutated
        Document working = doc.clone();

        for (String selector : NOISE_SELECTORS) {
            working.select(selector).remove();
        }

        String title = extractTitle(working);
        String text = extractText(working);

        log.debug("Extracted title='{}', textLength={}", title, text.length());
        return new ExtractionResult(title, text, rawHtml);
    }

    private String extractTitle(Document doc) {
        Elements h1 = doc.select("h1");
        if (!h1.isEmpty()) {
            String h1Text = h1.first().text().trim();
            if (!h1Text.isBlank()) {
                return h1Text;
            }
        }
        String titleTag = doc.title().trim();
        return titleTag.isBlank() ? "Untitled" : titleTag;
    }

    private String extractText(Document doc) {
        for (String selector : CONTENT_SELECTORS) {
            Element element = doc.selectFirst(selector);
            if (element == null) {
                continue;
            }

            // Build text with explicit paragraph separators by iterating block elements.
            // This preserves structural boundaries for the downstream chunker.
            StringBuilder sb = new StringBuilder();
            for (Element block : element.select("p, h1, h2, h3, h4, h5, li")) {
                String blockText = block.ownText().isBlank()
                    ? block.text().trim()
                    : block.text().trim();
                if (!blockText.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(blockText);
                }
            }

            String result = sb.toString().trim();
            if (result.length() > 100) {
                return result;
            }
        }

        // Final fallback: entire visible text of the document
        return doc.text().trim();
    }
}
