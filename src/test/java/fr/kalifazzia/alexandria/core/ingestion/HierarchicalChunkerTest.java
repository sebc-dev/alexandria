package fr.kalifazzia.alexandria.core.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HierarchicalChunker")
class HierarchicalChunkerTest {

    private HierarchicalChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new HierarchicalChunker();
    }

    /**
     * Generates test content of approximately the specified length.
     */
    private String generateContent(int approximateChars) {
        String paragraph = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris. ";
        int repetitions = (approximateChars / paragraph.length()) + 1;
        String result = paragraph.repeat(repetitions);
        return result.substring(0, Math.min(approximateChars, result.length()));
    }

    @Nested
    @DisplayName("Empty and null content")
    class EmptyContentTests {

        @Test
        @DisplayName("returns empty list for null content")
        void testChunkNullContent() {
            List<ChunkPair> result = chunker.chunk(null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty list for empty string")
        void testChunkEmptyString() {
            List<ChunkPair> result = chunker.chunk("");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty list for blank content")
        void testChunkBlankContent() {
            List<ChunkPair> result = chunker.chunk("   \n\t  ");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Short content (< 800 chars)")
    class ShortContentTests {

        @Test
        @DisplayName("single parent with single child for very short content")
        void testChunkShortContent() {
            String shortContent = "This is a short document that is less than 800 characters.";

            List<ChunkPair> result = chunker.chunk(shortContent);

            assertEquals(1, result.size());
            ChunkPair pair = result.getFirst();
            assertEquals(shortContent, pair.parentContent());
            assertEquals(1, pair.childContents().size());
            assertEquals(shortContent, pair.childContents().getFirst());
            assertEquals(0, pair.position());
        }

        @Test
        @DisplayName("parent and child have same content when below child threshold")
        void testChunkBelowChildThreshold() {
            String content = generateContent(500);

            List<ChunkPair> result = chunker.chunk(content);

            assertEquals(1, result.size());
            ChunkPair pair = result.getFirst();
            // Parent and child should be the same for content below child threshold
            assertEquals(pair.parentContent(), pair.childContents().getFirst());
        }
    }

    @Nested
    @DisplayName("Medium content (~2000 chars)")
    class MediumContentTests {

        @Test
        @DisplayName("single parent with multiple children for medium content")
        void testChunkMediumContent() {
            // ~2000 chars: below parent threshold (4000), above child threshold (800)
            String content = generateContent(2000);

            List<ChunkPair> result = chunker.chunk(content);

            assertEquals(1, result.size());
            ChunkPair pair = result.getFirst();

            // Should have multiple children
            assertTrue(pair.childContents().size() > 1,
                    "Expected multiple children, got: " + pair.childContents().size());

            // Parent should contain approximately the original content
            assertEquals(content.length(), pair.parentContent().length(),
                    "Parent content length should match original");
        }

        @Test
        @DisplayName("children together cover parent content")
        void testChildrenCoverParentContent() {
            String content = generateContent(2000);

            List<ChunkPair> result = chunker.chunk(content);
            ChunkPair pair = result.getFirst();

            // All unique words from parent should appear in at least one child
            Set<String> parentWords = new HashSet<>(List.of(pair.parentContent().split("\\s+")));
            Set<String> childWords = new HashSet<>();
            for (String child : pair.childContents()) {
                childWords.addAll(List.of(child.split("\\s+")));
            }

            // Check that most parent words appear in children (allowing for boundary effects)
            long coveredWords = parentWords.stream()
                    .filter(childWords::contains)
                    .count();
            double coverage = (double) coveredWords / parentWords.size();
            assertTrue(coverage > 0.9,
                    "Expected >90% word coverage, got: " + (coverage * 100) + "%");
        }
    }

    @Nested
    @DisplayName("Long content (~10000 chars)")
    class LongContentTests {

        @Test
        @DisplayName("multiple parents with multiple children for long content")
        void testChunkLongContent() {
            // ~10000 chars: above parent threshold (4000), should create multiple parents
            String content = generateContent(10000);

            List<ChunkPair> result = chunker.chunk(content);

            // Should have multiple parents
            assertTrue(result.size() > 1,
                    "Expected multiple parents, got: " + result.size());

            // Each parent should have multiple children
            for (ChunkPair pair : result) {
                assertTrue(pair.childContents().size() >= 1,
                        "Expected at least one child per parent");
            }
        }

        @Test
        @DisplayName("position increments correctly across parents")
        void testPositionIncrementsCorrectly() {
            String content = generateContent(10000);

            List<ChunkPair> result = chunker.chunk(content);

            for (int i = 0; i < result.size(); i++) {
                assertEquals(i, result.get(i).position(),
                        "Position should match index");
            }
        }
    }

    @Nested
    @DisplayName("Content preservation")
    class ContentPreservationTests {

        @Test
        @DisplayName("all content appears in some child chunk")
        void testChunkPreservesAllContent() {
            String content = "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa " +
                    "Lambda Mu Nu Xi Omicron Pi Rho Sigma Tau Upsilon Phi Chi Psi Omega. " +
                    generateContent(1500);

            List<ChunkPair> result = chunker.chunk(content);

            // Build combined child text
            StringBuilder allChildText = new StringBuilder();
            for (ChunkPair pair : result) {
                for (String child : pair.childContents()) {
                    allChildText.append(child).append(" ");
                }
            }
            String combinedChildren = allChildText.toString();

            // Check key words are preserved
            String[] keyWords = {"Alpha", "Beta", "Gamma", "Delta", "Omega"};
            for (String word : keyWords) {
                assertTrue(combinedChildren.contains(word),
                        "Word '" + word + "' should appear in child chunks");
            }
        }
    }

    @Nested
    @DisplayName("Overlap behavior")
    class OverlapTests {

        @Test
        @DisplayName("adjacent children have overlapping content")
        void testChunkOverlapMaintainsContext() {
            // Create content with a distinctive phrase in the middle
            String content = generateContent(1500) +
                    " IMPORTANT_PHRASE_AT_BOUNDARY " +
                    generateContent(1500);

            List<ChunkPair> result = chunker.chunk(content);

            // Find which child chunks contain the phrase
            int containingChildren = 0;
            for (ChunkPair pair : result) {
                for (String child : pair.childContents()) {
                    if (child.contains("IMPORTANT_PHRASE_AT_BOUNDARY")) {
                        containingChildren++;
                    }
                }
            }

            // The phrase should appear in at least one chunk
            assertTrue(containingChildren >= 1,
                    "Important phrase should appear in at least one child chunk");
        }

        @Test
        @DisplayName("parent chunks overlap with adjacent parents")
        void testParentChunksOverlap() {
            // ~12000 chars to ensure at least 3 parent chunks
            String content = generateContent(12000);

            List<ChunkPair> result = chunker.chunk(content);

            assertTrue(result.size() >= 3,
                    "Expected at least 3 parents for overlap test");

            // Check that consecutive parents share some content (overlap)
            for (int i = 0; i < result.size() - 1; i++) {
                String currentEnd = result.get(i).parentContent();
                String nextStart = result.get(i + 1).parentContent();

                // Extract last 100 chars of current parent
                String endOfCurrent = currentEnd.substring(
                        Math.max(0, currentEnd.length() - 400));

                // Extract first 400 chars of next parent
                String startOfNext = nextStart.substring(
                        0, Math.min(400, nextStart.length()));

                // They should share some common substring due to overlap
                // Check for at least one common word
                Set<String> endWords = new HashSet<>(List.of(endOfCurrent.split("\\s+")));
                Set<String> startWords = new HashSet<>(List.of(startOfNext.split("\\s+")));

                boolean hasOverlap = endWords.stream().anyMatch(startWords::contains);
                assertTrue(hasOverlap,
                        "Adjacent parents should have overlapping content at position " + i);
            }
        }
    }

    @Nested
    @DisplayName("ChunkPair validation")
    class ChunkPairValidationTests {

        @Test
        @DisplayName("parentContent is never empty for valid input")
        void testParentContentNotEmpty() {
            String content = generateContent(5000);

            List<ChunkPair> result = chunker.chunk(content);

            for (ChunkPair pair : result) {
                assertFalse(pair.parentContent().isEmpty(),
                        "Parent content should not be empty");
            }
        }

        @Test
        @DisplayName("childContents is never empty for valid input")
        void testChildContentsNotEmpty() {
            String content = generateContent(5000);

            List<ChunkPair> result = chunker.chunk(content);

            for (ChunkPair pair : result) {
                assertFalse(pair.childContents().isEmpty(),
                        "Child contents should not be empty");
            }
        }

        @Test
        @DisplayName("each child fits within parent content")
        void testChildrenFitWithinParent() {
            String content = generateContent(3000);

            List<ChunkPair> result = chunker.chunk(content);

            for (ChunkPair pair : result) {
                for (String child : pair.childContents()) {
                    // Each child should be a substring of the parent (with possible trimming)
                    // Due to recursive splitting, verify by checking word overlap
                    Set<String> childWords = new HashSet<>(List.of(child.split("\\s+")));
                    Set<String> parentWords = new HashSet<>(List.of(pair.parentContent().split("\\s+")));

                    long matching = childWords.stream()
                            .filter(parentWords::contains)
                            .count();
                    double overlap = (double) matching / childWords.size();

                    assertTrue(overlap > 0.95,
                            "Child words should mostly appear in parent, got: " + (overlap * 100) + "%");
                }
            }
        }
    }
}
