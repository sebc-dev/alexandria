package dev.alexandria.crawl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses llms.txt and llms-full.txt files to extract URLs and content.
 *
 * <p>The llms.txt format is a machine-readable Markdown file listing documentation pages:
 * <pre>
 * # Project Name
 *
 * > Optional summary
 *
 * ## Section
 *
 * - [Page Title](https://url): Optional description
 * </pre>
 *
 * <p>llms-full.txt is a concatenated Markdown document containing the actual documentation
 * content, intended for direct ingestion rather than as a link index.
 *
 * @see <a href="https://llmstxt.org/">llms.txt specification</a>
 */
public final class LlmsTxtParser {

    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");

    /**
     * Minimum ratio of lines containing markdown links to total non-blank, non-header lines
     * for content to be classified as a link index (llms.txt) rather than full content.
     */
    private static final double LINK_INDEX_THRESHOLD = 0.3;

    private LlmsTxtParser() {
        // utility class
    }

    /**
     * Extract URLs from llms.txt-format content.
     *
     * <p>Parses line-by-line, extracting URLs from Markdown link syntax:
     * {@code - [Title](URL)} or {@code [Title](URL)} (with or without leading dash).
     * Ignores header lines (starting with {@code #}), blank lines, and blockquote lines
     * (starting with {@code >}).
     *
     * @param llmsTxtContent the raw content of an llms.txt file
     * @return list of extracted URL strings, or empty list if content is null/blank
     */
    public static List<String> parseUrls(String llmsTxtContent) {
        if (llmsTxtContent == null || llmsTxtContent.isBlank()) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();
        for (String line : llmsTxtContent.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(">")) {
                continue;
            }
            Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                urls.add(matcher.group(2));
            }
        }
        return Collections.unmodifiableList(urls);
    }

    /**
     * Quick heuristic to determine if content is an llms.txt link index.
     *
     * <p>Returns {@code true} if the content starts with a {@code #} header and
     * contains a significant proportion of markdown links relative to content lines.
     * This distinguishes llms.txt (a link index) from llms-full.txt (raw Markdown content).
     *
     * @param content the content to check
     * @return true if content appears to be an llms.txt link index
     */
    public static boolean isLlmsTxtContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        boolean startsWithHeader = false;
        int contentLines = 0;
        int linkLines = 0;

        for (String line : content.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!startsWithHeader) {
                startsWithHeader = trimmed.startsWith("#");
                if (!startsWithHeader) {
                    return false;
                }
                continue;
            }
            if (trimmed.startsWith("#") || trimmed.startsWith(">")) {
                continue;
            }
            contentLines++;
            if (MARKDOWN_LINK_PATTERN.matcher(trimmed).find()) {
                linkLines++;
            }
        }

        if (contentLines == 0) {
            return false;
        }

        return (double) linkLines / contentLines >= LINK_INDEX_THRESHOLD;
    }

    /**
     * Parse content and classify it as llms.txt (link index) or llms-full.txt (raw content).
     *
     * <p>If the content looks like a link index: extracts URLs, rawContent is empty.
     * If the content looks like full documentation: extracts any inline URLs,
     * rawContent contains the full content for direct ingestion.
     *
     * @param content the raw content of an llms.txt or llms-full.txt file
     * @return parsed result with URLs, raw content, and content type flag
     */
    public static LlmsTxtResult parse(String content) {
        if (content == null || content.isBlank()) {
            return new LlmsTxtResult(List.of(), "", false);
        }

        List<String> urls = parseUrls(content);
        boolean isIndex = isLlmsTxtContent(content);

        if (isIndex) {
            return new LlmsTxtResult(urls, "", false);
        } else {
            return new LlmsTxtResult(urls, content, true);
        }
    }

    /**
     * Result of parsing llms.txt or llms-full.txt content.
     *
     * @param urls extracted URLs from markdown links
     * @param rawContent the original content (non-empty only for llms-full.txt)
     * @param isFullContent true when content is llms-full.txt (large Markdown for direct ingestion)
     */
    public record LlmsTxtResult(List<String> urls, String rawContent, boolean isFullContent) {

        public LlmsTxtResult {
            urls = urls == null ? List.of() : List.copyOf(urls);
            rawContent = rawContent == null ? "" : rawContent;
        }
    }
}
