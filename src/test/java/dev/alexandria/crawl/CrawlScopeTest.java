package dev.alexandria.crawl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlScopeTest {

    @Test
    void nullPatternsDefaultToEmptyLists() {
        var scope = new CrawlScope(null, null, null, 100);

        assertThat(scope.allowPatterns()).isEmpty();
        assertThat(scope.blockPatterns()).isEmpty();
    }

    @Test
    void patternsAreDefensivelyCopied() {
        var allowList = new ArrayList<>(List.of("/docs/**"));
        var blockList = new ArrayList<>(List.of("/docs/archive/**"));
        var scope = new CrawlScope(allowList, blockList, 3, 50);

        allowList.add("/api/**");
        blockList.add("/internal/**");

        assertThat(scope.allowPatterns()).containsExactly("/docs/**");
        assertThat(scope.blockPatterns()).containsExactly("/docs/archive/**");
    }

    @Test
    void withDefaultsReturnsEmptyScope() {
        var scope = CrawlScope.withDefaults(200);

        assertThat(scope.allowPatterns()).isEmpty();
        assertThat(scope.blockPatterns()).isEmpty();
        assertThat(scope.maxDepth()).isNull();
        assertThat(scope.maxPages()).isEqualTo(200);
    }
}
