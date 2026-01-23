package fr.kalifazzia.alexandria.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Document record.
 * Tests factory method null handling and defensive copying.
 */
class DocumentTest {

    @Test
    void create_withNullTags_returnsEmptyList() {
        Document doc = Document.create(
                "/path/to/doc.md",
                "Title",
                "category",
                null,  // null tags
                "hash123",
                Map.of("key", "value")
        );

        assertThat(doc.tags()).isNotNull();
        assertThat(doc.tags()).isEmpty();
    }

    @Test
    void create_withNullFrontmatter_returnsEmptyMap() {
        Document doc = Document.create(
                "/path/to/doc.md",
                "Title",
                "category",
                List.of("tag1"),
                "hash123",
                null  // null frontmatter
        );

        assertThat(doc.frontmatter()).isNotNull();
        assertThat(doc.frontmatter()).isEmpty();
    }

    @Test
    void create_withBothNullTagsAndFrontmatter_returnsEmptyCollections() {
        Document doc = Document.create(
                "/path/to/doc.md",
                "Title",
                "category",
                null,  // null tags
                "hash123",
                null   // null frontmatter
        );

        assertThat(doc.tags()).isNotNull().isEmpty();
        assertThat(doc.frontmatter()).isNotNull().isEmpty();
    }

    @Test
    void create_withValidTags_returnsCopy() {
        List<String> originalTags = List.of("tag1", "tag2");

        Document doc = Document.create(
                "/path/to/doc.md",
                "Title",
                "category",
                originalTags,
                "hash123",
                Map.of()
        );

        assertThat(doc.tags()).containsExactly("tag1", "tag2");
    }

    @Test
    void create_withValidFrontmatter_returnsCopy() {
        Map<String, Object> originalFrontmatter = Map.of("author", "John", "version", 1);

        Document doc = Document.create(
                "/path/to/doc.md",
                "Title",
                "category",
                List.of(),
                "hash123",
                originalFrontmatter
        );

        assertThat(doc.frontmatter()).containsEntry("author", "John");
        assertThat(doc.frontmatter()).containsEntry("version", 1);
    }

    @Test
    void constructor_withNullTags_returnsEmptyList() {
        Document doc = new Document(
                null,
                "/path/to/doc.md",
                "Title",
                "category",
                null,  // null tags
                "hash123",
                Map.of(),
                null,
                null
        );

        assertThat(doc.tags()).isNotNull();
        assertThat(doc.tags()).isEmpty();
    }

    @Test
    void constructor_withNullFrontmatter_returnsEmptyMap() {
        Document doc = new Document(
                null,
                "/path/to/doc.md",
                "Title",
                "category",
                List.of(),
                "hash123",
                null,  // null frontmatter
                null,
                null
        );

        assertThat(doc.frontmatter()).isNotNull();
        assertThat(doc.frontmatter()).isEmpty();
    }
}
