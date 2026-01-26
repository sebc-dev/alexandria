package fr.kalifazzia.alexandria.infra.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.kalifazzia.alexandria.core.evaluation.GoldenQuery;
import fr.kalifazzia.alexandria.core.evaluation.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonlGoldenDatasetLoaderTest {

    @TempDir
    Path tempDir;

    private JsonlGoldenDatasetLoader loader;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        loader = new JsonlGoldenDatasetLoader(objectMapper);
    }

    @Test
    void load_validJsonl_parsesAllQueries() throws IOException {
        Path testFile = Path.of("src/test/resources/evaluation/test-golden-dataset.jsonl");

        List<GoldenQuery> queries = loader.load(testFile);

        assertThat(queries).hasSize(3);

        GoldenQuery first = queries.get(0);
        assertThat(first.query()).isEqualTo("How to configure Spring Boot?");
        assertThat(first.expectedDocIds()).containsExactly(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
        assertThat(first.requiresKg()).isFalse();
        assertThat(first.reasoningHops()).isZero();
        assertThat(first.questionType()).isEqualTo(QuestionType.FACTUAL);
    }

    @Test
    void load_emptyLines_skipsGracefully() throws IOException {
        Path testFile = tempDir.resolve("with-empty-lines.jsonl");
        Files.writeString(testFile, """
                {"query": "Test query", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440001"], "requires_kg": false, "reasoning_hops": 0, "question_type": "factual"}

                {"query": "Another query", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440002"], "requires_kg": false, "reasoning_hops": 0, "question_type": "factual"}

                """);

        List<GoldenQuery> queries = loader.load(testFile);

        assertThat(queries).hasSize(2);
        assertThat(queries.get(0).query()).isEqualTo("Test query");
        assertThat(queries.get(1).query()).isEqualTo("Another query");
    }

    @Test
    void load_invalidJson_throwsWithLineNumber() throws IOException {
        Path testFile = tempDir.resolve("invalid.jsonl");
        Files.writeString(testFile, """
                {"query": "Valid", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440001"], "requires_kg": false, "reasoning_hops": 0, "question_type": "factual"}
                not valid json
                {"query": "Another valid", "expected_doc_ids": ["550e8400-e29b-41d4-a716-446655440002"], "requires_kg": false, "reasoning_hops": 0, "question_type": "factual"}
                """);

        assertThatThrownBy(() -> loader.load(testFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid JSON at line 2");
    }

    @Test
    void load_missingRequiredField_throwsValidationError() throws IOException {
        Path testFile = tempDir.resolve("missing-field.jsonl");
        Files.writeString(testFile, """
                {"query": "Test", "requires_kg": false, "reasoning_hops": 0, "question_type": "factual"}
                """);

        assertThatThrownBy(() -> loader.load(testFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid JSON at line 1");
    }

    @Test
    void load_allQuestionTypes_parsesCorrectly() throws IOException {
        Path testFile = Path.of("src/test/resources/evaluation/test-golden-dataset.jsonl");

        List<GoldenQuery> queries = loader.load(testFile);

        assertThat(queries)
                .extracting(GoldenQuery::questionType)
                .containsExactly(QuestionType.FACTUAL, QuestionType.MULTI_HOP, QuestionType.GRAPH_TRAVERSAL);
    }

    @Test
    void load_multiHopQuery_parsesMultipleExpectedDocIds() throws IOException {
        Path testFile = Path.of("src/test/resources/evaluation/test-golden-dataset.jsonl");

        List<GoldenQuery> queries = loader.load(testFile);

        GoldenQuery multiHop = queries.get(1);
        assertThat(multiHop.questionType()).isEqualTo(QuestionType.MULTI_HOP);
        assertThat(multiHop.expectedDocIds()).hasSize(2);
        assertThat(multiHop.reasoningHops()).isEqualTo(1);
    }

    @Test
    void load_graphTraversalQuery_hasRequiresKgTrue() throws IOException {
        Path testFile = Path.of("src/test/resources/evaluation/test-golden-dataset.jsonl");

        List<GoldenQuery> queries = loader.load(testFile);

        GoldenQuery graphQuery = queries.get(2);
        assertThat(graphQuery.questionType()).isEqualTo(QuestionType.GRAPH_TRAVERSAL);
        assertThat(graphQuery.requiresKg()).isTrue();
        assertThat(graphQuery.reasoningHops()).isEqualTo(2);
    }
}
