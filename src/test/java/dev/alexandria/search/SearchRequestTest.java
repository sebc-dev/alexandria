package dev.alexandria.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestTest {

    @Test
    void defaultMaxResultsIsTen() {
        SearchRequest request = new SearchRequest("query");
        assertThat(request.maxResults()).isEqualTo(10);
    }

    @Test
    void customMaxResultsIsRespected() {
        SearchRequest request = new SearchRequest("query", 5);
        assertThat(request.maxResults()).isEqualTo(5);
    }

    @Test
    void nullQueryThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchRequest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query must not be blank");
    }

    @Test
    void blankQueryThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchRequest("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query must not be blank");
    }

    @Test
    void maxResultsLessThanOneThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new SearchRequest("query", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxResults must be at least 1");
    }
}
