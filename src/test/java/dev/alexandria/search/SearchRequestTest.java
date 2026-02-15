package dev.alexandria.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestTest {

    @Test
    void default_maxResults_is_10() {
        SearchRequest request = new SearchRequest("query");
        assertThat(request.maxResults()).isEqualTo(10);
    }

    @Test
    void custom_maxResults_is_respected() {
        SearchRequest request = new SearchRequest("query", 5);
        assertThat(request.maxResults()).isEqualTo(5);
    }

    @Test
    void null_query_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> new SearchRequest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query must not be blank");
    }

    @Test
    void blank_query_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> new SearchRequest("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query must not be blank");
    }

    @Test
    void maxResults_less_than_1_throws_IllegalArgumentException() {
        assertThatThrownBy(() -> new SearchRequest("query", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxResults must be at least 1");
    }
}
