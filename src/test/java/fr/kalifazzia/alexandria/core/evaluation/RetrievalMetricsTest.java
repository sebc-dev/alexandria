package fr.kalifazzia.alexandria.core.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RetrievalMetrics")
class RetrievalMetricsTest {

    /**
     * Creates a deterministic UUID from a seed for test readability.
     */
    private UUID uuid(int seed) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", seed));
    }

    @Nested
    @DisplayName("precisionAtK")
    class PrecisionAtK {

        @Test
        @DisplayName("returns 1.0 when all retrieved are relevant")
        void precisionAtK_allRelevant_returns1() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3), uuid(4));

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 4);

            assertThat(precision).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.5 when half are relevant")
        void precisionAtK_halfRelevant_returns50Percent() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
            Set<UUID> relevant = Set.of(uuid(1), uuid(3));

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 4);

            assertThat(precision).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when none are relevant")
        void precisionAtK_noneRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
            Set<UUID> relevant = Set.of(uuid(5), uuid(6));

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 4);

            assertThat(precision).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("divides by k when k is larger than retrieved size")
        void precisionAtK_kLargerThanRetrieved_usesK() {
            // 2 retrieved, k=5, 1 relevant -> 1/5 = 0.2
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Set.of(uuid(1));

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 5);

            assertThat(precision).isCloseTo(0.2, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when k is zero")
        void precisionAtK_kZero_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Set.of(uuid(1));

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 0);

            assertThat(precision).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when retrieved list is empty")
        void precisionAtK_emptyRetrieved_returns0() {
            List<UUID> retrieved = Collections.emptyList();
            Set<UUID> relevant = Set.of(uuid(1), uuid(2));

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 5);

            assertThat(precision).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when relevant set is empty")
        void precisionAtK_emptyRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Collections.emptySet();

            double precision = RetrievalMetrics.precisionAtK(retrieved, relevant, 2);

            assertThat(precision).isCloseTo(0.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("recallAtK")
    class RecallAtK {

        @Test
        @DisplayName("returns 1.0 when all relevant are retrieved")
        void recallAtK_allRelevantRetrieved_returns1() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3));

            double recall = RetrievalMetrics.recallAtK(retrieved, relevant, 3);

            assertThat(recall).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.5 when half of relevant are retrieved")
        void recallAtK_halfRetrieved_returns50Percent() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(5), uuid(6), uuid(7),
                    uuid(8), uuid(9), uuid(10), uuid(11), uuid(12));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3), uuid(4));

            double recall = RetrievalMetrics.recallAtK(retrieved, relevant, 10);

            assertThat(recall).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when relevant set is empty")
        void recallAtK_emptyRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Collections.emptySet();

            double recall = RetrievalMetrics.recallAtK(retrieved, relevant, 2);

            assertThat(recall).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when none of the relevant are retrieved")
        void recallAtK_noneRetrieved_returns0() {
            List<UUID> retrieved = List.of(uuid(5), uuid(6), uuid(7));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3));

            double recall = RetrievalMetrics.recallAtK(retrieved, relevant, 3);

            assertThat(recall).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("handles k larger than retrieved size")
        void recallAtK_kLargerThanRetrieved() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3), uuid(4));

            double recall = RetrievalMetrics.recallAtK(retrieved, relevant, 10);

            // 2 relevant found out of 4 total = 0.5
            assertThat(recall).isCloseTo(0.5, within(0.001));
        }
    }

    @Nested
    @DisplayName("reciprocalRank")
    class ReciprocalRank {

        @Test
        @DisplayName("returns 1.0 when relevant is at rank 1")
        void reciprocalRank_relevantAtRank1_returns1() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3));
            Set<UUID> relevant = Set.of(uuid(1));

            double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

            assertThat(mrr).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("returns 1/3 when relevant is at rank 3")
        void reciprocalRank_relevantAtRank3_returnsOneThird() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
            Set<UUID> relevant = Set.of(uuid(3));

            double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

            assertThat(mrr).isCloseTo(0.333, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when no relevant found")
        void reciprocalRank_noRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3));
            Set<UUID> relevant = Set.of(uuid(5), uuid(6));

            double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

            assertThat(mrr).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns rank of first relevant when multiple relevant exist")
        void reciprocalRank_multipleRelevant_returnsFirstRank() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4));
            Set<UUID> relevant = Set.of(uuid(2), uuid(4));

            double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

            // First relevant is at position 1 (rank 2), so 1/2 = 0.5
            assertThat(mrr).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when retrieved list is empty")
        void reciprocalRank_emptyRetrieved_returns0() {
            List<UUID> retrieved = Collections.emptyList();
            Set<UUID> relevant = Set.of(uuid(1));

            double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

            assertThat(mrr).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when relevant set is empty")
        void reciprocalRank_emptyRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Collections.emptySet();

            double mrr = RetrievalMetrics.reciprocalRank(retrieved, relevant);

            assertThat(mrr).isCloseTo(0.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("ndcgAtK")
    class NdcgAtK {

        @Test
        @DisplayName("returns 1.0 for perfect ranking")
        void ndcgAtK_perfectRanking_returns1() {
            // All relevant documents at the top
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(3), uuid(4), uuid(5));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2), uuid(3));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 3);

            assertThat(ndcg).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("returns less than 1.0 for imperfect ranking")
        void ndcgAtK_worstRanking_returnsLessThan1() {
            // Relevant document at the end
            List<UUID> retrieved = List.of(uuid(5), uuid(6), uuid(1));
            Set<UUID> relevant = Set.of(uuid(1));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 3);

            // DCG = 1/log2(3+1) = 1/2 = 0.5
            // IDCG = 1/log2(0+2) = 1/1 = 1.0
            // NDCG = 0.5 / 1.0 = 0.5
            assertThat(ndcg).isCloseTo(0.5, within(0.001));
            assertThat(ndcg).isLessThan(1.0);
        }

        @Test
        @DisplayName("returns 0.0 when no relevant in top k")
        void ndcgAtK_noRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(5), uuid(6), uuid(7));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 3);

            assertThat(ndcg).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 1.0 for single relevant at first position")
        void ndcgAtK_singleRelevantFirst_returns1() {
            List<UUID> retrieved = List.of(uuid(1), uuid(5), uuid(6));
            Set<UUID> relevant = Set.of(uuid(1));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 3);

            assertThat(ndcg).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("handles k larger than relevant count correctly")
        void ndcgAtK_kLargerThanRelevant_handlesCorrectly() {
            // k=10, only 2 relevant documents
            List<UUID> retrieved = List.of(uuid(1), uuid(2), uuid(5), uuid(6), uuid(7),
                    uuid(8), uuid(9), uuid(10), uuid(11), uuid(12));
            Set<UUID> relevant = Set.of(uuid(1), uuid(2));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 10);

            // Both relevant at top, so perfect NDCG
            assertThat(ndcg).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when k is zero")
        void ndcgAtK_kZero_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Set.of(uuid(1));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 0);

            assertThat(ndcg).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when retrieved list is empty")
        void ndcgAtK_emptyRetrieved_returns0() {
            List<UUID> retrieved = Collections.emptyList();
            Set<UUID> relevant = Set.of(uuid(1));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 5);

            assertThat(ndcg).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns 0.0 when relevant set is empty")
        void ndcgAtK_emptyRelevant_returns0() {
            List<UUID> retrieved = List.of(uuid(1), uuid(2));
            Set<UUID> relevant = Collections.emptySet();

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 2);

            assertThat(ndcg).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("uses log base 2 for discounting")
        void ndcgAtK_usesLog2() {
            // Verify the log base 2 calculation by checking a known value
            // Position 0: discount = 1/log2(2) = 1/1 = 1
            // Position 1: discount = 1/log2(3) = ~0.631
            // Position 2: discount = 1/log2(4) = 1/2 = 0.5
            List<UUID> retrieved = List.of(uuid(5), uuid(1), uuid(6));
            Set<UUID> relevant = Set.of(uuid(1));

            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, relevant, 3);

            // DCG = 1/log2(3) = 1/1.585 = 0.631
            // IDCG = 1/log2(2) = 1/1 = 1.0
            // NDCG = 0.631 / 1.0 = 0.631
            assertThat(ndcg).isCloseTo(0.631, within(0.001));
        }
    }
}
