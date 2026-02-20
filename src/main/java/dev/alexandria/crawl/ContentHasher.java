package dev.alexandria.crawl;

/**
 * Static utility for computing SHA-256 content hashes.
 * Used for incremental change detection during crawling.
 */
public final class ContentHasher {

    private ContentHasher() {
        // utility class
    }

    /**
     * Compute the SHA-256 hash of the given content.
     *
     * @param content the content to hash
     * @return lowercase hex string of the SHA-256 hash
     */
    public static String sha256(String content) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
