package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentHasherTest {

  @Test
  void hashOfKnownInputMatchesExpected() {
    String result = ContentHasher.sha256("hello");

    assertThat(result)
        .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  }

  @Test
  void sameContentProducesSameHash() {
    String first = ContentHasher.sha256("deterministic input");
    String second = ContentHasher.sha256("deterministic input");

    assertThat(first).isEqualTo(second);
  }

  @Test
  void differentContentProducesDifferentHash() {
    String first = ContentHasher.sha256("content A");
    String second = ContentHasher.sha256("content B");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void emptyStringProducesValidHash() {
    String result = ContentHasher.sha256("");

    assertThat(result)
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }
}
