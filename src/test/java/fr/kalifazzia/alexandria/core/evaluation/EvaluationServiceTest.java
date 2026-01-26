package fr.kalifazzia.alexandria.core.evaluation;

import fr.kalifazzia.alexandria.core.port.GoldenDatasetLoader;
import fr.kalifazzia.alexandria.core.search.SearchPort;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private SearchPort searchPort;

    @Mock
    private GoldenDatasetLoader datasetLoader;

    private EvaluationService evaluationService;

    private static final Path TEST_DATASET_PATH = Path.of("/test/dataset.jsonl");

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationService(searchPort, datasetLoader);
    }

    @Test
    void evaluate_singleQuery_calculatesAllMetrics() throws IOException {
        // Given: one golden query expecting doc1
        UUID doc1 = UUID.randomUUID();
        GoldenQuery goldenQuery = new GoldenQuery(
                "test query",
                List.of(doc1),
                false,
                1,
                QuestionType.FACTUAL
        );
        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of(goldenQuery));

        // Mock search returns doc1 as first result
        SearchResult result = createSearchResult(doc1);
        when(searchPort.hybridSearch("test query", 20)).thenReturn(List.of(result));

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: all metrics should be calculated
        assertThat(report.overall().queryCount()).isEqualTo(1);
        // With 1 relevant in top 1, P@5 = 1/5 = 0.2
        assertThat(report.overall().precisionAt5()).isCloseTo(0.2, within(0.001));
        // R@10 = 1/1 = 1.0 (found all relevant)
        assertThat(report.overall().recallAt10()).isCloseTo(1.0, within(0.001));
        // MRR = 1/1 = 1.0 (first result is relevant)
        assertThat(report.overall().mrr()).isCloseTo(1.0, within(0.001));
        // NDCG@10: DCG = 1/log2(2) = 1.0, IDCG = 1.0, NDCG = 1.0
        assertThat(report.overall().ndcgAt10()).isCloseTo(1.0, within(0.001));

        // Details should contain the query evaluation
        assertThat(report.details()).hasSize(1);
        assertThat(report.details().get(0).query()).isEqualTo("test query");
    }

    @Test
    void evaluate_multipleQueries_averagesMetrics() throws IOException {
        // Given: two golden queries
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        UUID doc3 = UUID.randomUUID();

        GoldenQuery query1 = new GoldenQuery("query 1", List.of(doc1), false, 1, QuestionType.FACTUAL);
        GoldenQuery query2 = new GoldenQuery("query 2", List.of(doc2), false, 1, QuestionType.FACTUAL);

        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of(query1, query2));

        // Query 1: returns doc1 (relevant)
        when(searchPort.hybridSearch("query 1", 20))
                .thenReturn(List.of(createSearchResult(doc1)));

        // Query 2: returns doc3 (not relevant), doc2 (relevant) at position 2
        when(searchPort.hybridSearch("query 2", 20))
                .thenReturn(List.of(createSearchResult(doc3), createSearchResult(doc2)));

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: metrics are averaged
        assertThat(report.overall().queryCount()).isEqualTo(2);
        // Query 1 MRR = 1.0, Query 2 MRR = 0.5, average = 0.75
        assertThat(report.overall().mrr()).isCloseTo(0.75, within(0.001));
    }

    @Test
    void evaluate_segmentsByQuestionType() throws IOException {
        // Given: three queries, one of each type
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        UUID doc3 = UUID.randomUUID();

        GoldenQuery factual = new GoldenQuery("factual query", List.of(doc1), false, 1, QuestionType.FACTUAL);
        GoldenQuery multiHop = new GoldenQuery("multi-hop query", List.of(doc2), false, 2, QuestionType.MULTI_HOP);
        GoldenQuery graphQuery = new GoldenQuery("graph query", List.of(doc3), true, 3, QuestionType.GRAPH_TRAVERSAL);

        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of(factual, multiHop, graphQuery));

        // All queries return their expected doc
        when(searchPort.hybridSearch("factual query", 20))
                .thenReturn(List.of(createSearchResult(doc1)));
        when(searchPort.hybridSearch("multi-hop query", 20))
                .thenReturn(List.of(createSearchResult(doc2)));
        when(searchPort.hybridSearch("graph query", 20))
                .thenReturn(List.of(createSearchResult(doc3)));

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: byQuestionType map has 3 entries
        assertThat(report.byQuestionType()).hasSize(3);
        assertThat(report.byQuestionType()).containsKeys(
                QuestionType.FACTUAL,
                QuestionType.MULTI_HOP,
                QuestionType.GRAPH_TRAVERSAL
        );

        // Each type has 1 query
        assertThat(report.byQuestionType().get(QuestionType.FACTUAL).queryCount()).isEqualTo(1);
        assertThat(report.byQuestionType().get(QuestionType.MULTI_HOP).queryCount()).isEqualTo(1);
        assertThat(report.byQuestionType().get(QuestionType.GRAPH_TRAVERSAL).queryCount()).isEqualTo(1);
    }

    @Test
    void evaluate_deduplicatesDocumentIds() throws IOException {
        // Given: query expects doc1
        UUID doc1 = UUID.randomUUID();
        GoldenQuery goldenQuery = new GoldenQuery(
                "test query",
                List.of(doc1),
                false,
                1,
                QuestionType.FACTUAL
        );
        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of(goldenQuery));

        // Search returns TWO chunks from the same document
        SearchResult chunk1 = createSearchResult(doc1);
        SearchResult chunk2 = createSearchResult(doc1); // Same doc ID, different chunk
        when(searchPort.hybridSearch("test query", 20))
                .thenReturn(List.of(chunk1, chunk2));

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: document should be counted only once
        assertThat(report.details()).hasSize(1);
        // Retrieved doc IDs should have doc1 only once
        assertThat(report.details().get(0).retrievedDocIds()).containsExactly(doc1);
        // P@5 = 1/5 = 0.2 (not 2/5 = 0.4)
        assertThat(report.overall().precisionAt5()).isCloseTo(0.2, within(0.001));
    }

    @Test
    void evaluate_emptyDataset_returnsEmptyReport() throws IOException {
        // Given: empty dataset
        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of());

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: empty report with zero metrics
        assertThat(report.overall().queryCount()).isZero();
        assertThat(report.overall().precisionAt5()).isZero();
        assertThat(report.overall().passed()).isFalse();
        assertThat(report.details()).isEmpty();
        assertThat(report.byQuestionType()).isEmpty();
        assertThat(report.evaluatedAt()).isNotNull();
    }

    @Test
    void evaluate_passedTrue_whenMetricsMeetThresholds() throws IOException {
        // Given: query with good results (high P@5 and NDCG@10)
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        UUID doc3 = UUID.randomUUID();

        // Query expects 3 docs
        GoldenQuery goldenQuery = new GoldenQuery(
                "test query",
                List.of(doc1, doc2, doc3),
                false,
                1,
                QuestionType.FACTUAL
        );
        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of(goldenQuery));

        // Returns all 3 relevant docs in top 3
        when(searchPort.hybridSearch("test query", 20))
                .thenReturn(List.of(
                        createSearchResult(doc1),
                        createSearchResult(doc2),
                        createSearchResult(doc3)
                ));

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: P@5 = 3/5 = 0.6 >= 0.5, NDCG@10 = 1.0 >= 0.5 -> passed=true
        assertThat(report.overall().precisionAt5()).isCloseTo(0.6, within(0.001));
        assertThat(report.overall().ndcgAt10()).isCloseTo(1.0, within(0.001));
        assertThat(report.overall().passed()).isTrue();
    }

    @Test
    void evaluate_passedFalse_whenMetricsBelowThresholds() throws IOException {
        // Given: query with poor results
        UUID doc1 = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();

        GoldenQuery goldenQuery = new GoldenQuery(
                "test query",
                List.of(doc1),
                false,
                1,
                QuestionType.FACTUAL
        );
        when(datasetLoader.load(TEST_DATASET_PATH)).thenReturn(List.of(goldenQuery));

        // Search returns unrelated document (not doc1)
        when(searchPort.hybridSearch("test query", 20))
                .thenReturn(List.of(createSearchResult(unrelated)));

        // When
        EvaluationReport report = evaluationService.evaluate(TEST_DATASET_PATH);

        // Then: P@5 = 0, NDCG@10 = 0 -> passed=false
        assertThat(report.overall().precisionAt5()).isZero();
        assertThat(report.overall().ndcgAt10()).isZero();
        assertThat(report.overall().passed()).isFalse();
    }

    private SearchResult createSearchResult(UUID documentId) {
        return new SearchResult(
                UUID.randomUUID(),      // childChunkId
                "chunk content",        // childContent
                0,                      // childPosition
                UUID.randomUUID(),      // parentChunkId
                "parent context",       // parentContext
                documentId,             // documentId (important!)
                "Document Title",       // documentTitle
                "/path/to/doc.md",      // documentPath
                "test",                 // category
                List.of("tag1"),        // tags
                0.85                    // similarity
        );
    }
}
