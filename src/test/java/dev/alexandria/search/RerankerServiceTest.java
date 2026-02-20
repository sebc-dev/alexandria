package dev.alexandria.search;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RerankerServiceTest {

    @Mock
    ScoringModel scoringModel;

    @InjectMocks
    RerankerService rerankerService;

    private static final Embedding DUMMY_EMBEDDING = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});

    @Test
    void reranksResultsByScore() {
        var candidates = List.of(
                match("text A", 0.7, "https://a.com", "Section A"),
                match("text B", 0.8, "https://b.com", "Section B"),
                match("text C", 0.6, "https://c.com", "Section C")
        );
        given(scoringModel.scoreAll(anyList(), anyString()))
                .willReturn(Response.from(List.of(0.8, 0.2, 0.5)));

        List<SearchResult> results = rerankerService.rerank("test query", candidates, 10, null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.8);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.5);
        assertThat(results.get(2).rerankScore()).isEqualTo(0.2);
        assertThat(results.get(0).text()).isEqualTo("text A");
        assertThat(results.get(1).text()).isEqualTo("text C");
        assertThat(results.get(2).text()).isEqualTo("text B");
    }

    @Test
    void respectsMaxResultsLimit() {
        var candidates = List.of(
                match("text A", 0.9, "https://a.com", "A"),
                match("text B", 0.8, "https://b.com", "B"),
                match("text C", 0.7, "https://c.com", "C"),
                match("text D", 0.6, "https://d.com", "D"),
                match("text E", 0.5, "https://e.com", "E")
        );
        given(scoringModel.scoreAll(anyList(), anyString()))
                .willReturn(Response.from(List.of(0.1, 0.9, 0.5, 0.3, 0.7)));

        List<SearchResult> results = rerankerService.rerank("test query", candidates, 2, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.9);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.7);
    }

    @Test
    void respectsMinScoreThreshold() {
        var candidates = List.of(
                match("text A", 0.9, "https://a.com", "A"),
                match("text B", 0.8, "https://b.com", "B"),
                match("text C", 0.7, "https://c.com", "C")
        );
        given(scoringModel.scoreAll(anyList(), anyString()))
                .willReturn(Response.from(List.of(0.8, 0.2, 0.5)));

        List<SearchResult> results = rerankerService.rerank("test query", candidates, 10, 0.4);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.8);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.5);
    }

    @Test
    void returnsEmptyListWhenNoCandidates() {
        List<SearchResult> results = rerankerService.rerank("test query", List.of(), 10, null);

        assertThat(results).isEmpty();
    }

    @Test
    void propagatesScoringModelException() {
        var candidates = List.of(match("text A", 0.9, "https://a.com", "A"));
        given(scoringModel.scoreAll(anyList(), anyString()))
                .willThrow(new RuntimeException("ONNX model error"));

        assertThatThrownBy(() -> rerankerService.rerank("test query", candidates, 10, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ONNX model error");
    }

    @Test
    void preservesMetadataFromOriginalResults() {
        var candidates = List.of(
                match("content text", 0.75, "https://docs.spring.io/boot", "Getting Started > Quick Start")
        );
        given(scoringModel.scoreAll(anyList(), anyString()))
                .willReturn(Response.from(List.of(0.92)));

        List<SearchResult> results = rerankerService.rerank("spring boot", candidates, 10, null);

        assertThat(results).hasSize(1);
        SearchResult result = results.getFirst();
        assertThat(result.text()).isEqualTo("content text");
        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.sourceUrl()).isEqualTo("https://docs.spring.io/boot");
        assertThat(result.sectionPath()).isEqualTo("Getting Started > Quick Start");
        assertThat(result.rerankScore()).isEqualTo(0.92);
    }

    @Test
    void minScoreNullMeansNoThreshold() {
        var candidates = List.of(
                match("text A", 0.9, "https://a.com", "A"),
                match("text B", 0.8, "https://b.com", "B"),
                match("text C", 0.7, "https://c.com", "C")
        );
        given(scoringModel.scoreAll(anyList(), anyString()))
                .willReturn(Response.from(List.of(0.01, 0.001, 0.0001)));

        List<SearchResult> results = rerankerService.rerank("test query", candidates, 10, null);

        assertThat(results).hasSize(3);
    }

    private EmbeddingMatch<TextSegment> match(String text, double score, String sourceUrl, String sectionPath) {
        TextSegment segment = TextSegment.from(
                text,
                Metadata.from("source_url", sourceUrl).put("section_path", sectionPath)
        );
        return new EmbeddingMatch<>(score, "id-" + text.hashCode(), DUMMY_EMBEDDING, segment);
    }
}
