package dev.alexandria.crawl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Static utility for computing SHA-256 content hashes. Used for incremental change detection during
 * crawling.
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
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
