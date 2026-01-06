# MCP Response Format (from research #6)

**Principe:** Dual-format response - JSON structuré + Markdown lisible. Limite 8000 tokens par réponse (sous le warning 10K de Claude Code).

```java
package dev.alexandria.adapters;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper pour formater les réponses MCP avec dual-format.
 * Inclut metadata inline pour Claude + structured pour parsing.
 */
public class McpResponseFormatter {
    private static final int MAX_TOKENS = 8000;
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Format dual: JSON structuré + Markdown lisible.
     */
    public static CallToolResult formatSearchResults(McpSearchResponse response) {
        var builder = CallToolResult.builder();

        // 1. Structured JSON content (pour parsing programmatique)
        try {
            builder.addTextContent(mapper.writeValueAsString(response));
        } catch (Exception e) {
            // Fallback si JSON échoue
        }

        // 2. Markdown content (pour Claude et lisibilité humaine)
        StringBuilder md = new StringBuilder();

        if (response.metadata().status() == SearchStatus.SUCCESS
            || response.metadata().status() == SearchStatus.PARTIAL) {

            md.append("## Search Results\n\n");
            if (response.metadata().message() != null) {
                md.append("_").append(response.metadata().message()).append("_\n\n");
            }

            int tokenBudget = MAX_TOKENS - 500; // Reserve for metadata
            int usedTokens = 0;

            for (var result : response.results()) {
                String entry = formatResultEntry(result);
                int entryTokens = estimateTokens(entry);

                if (usedTokens + entryTokens > tokenBudget) {
                    md.append("\n---\n_[Content truncated. ")
                      .append(response.results().size() - response.results().indexOf(result))
                      .append(" more results available]_\n");
                    break;
                }

                md.append(entry);
                usedTokens += entryTokens;
            }

            md.append("\n---\n")
              .append("_Found ").append(response.metadata().returnedResults())
              .append(" of ").append(response.metadata().totalCandidates())
              .append(" candidates_\n");
        } else {
            md.append("## No Results\n\n")
              .append(response.metadata().message());
        }

        builder.addTextContent(md.toString());
        return builder.build();
    }

    private static String formatResultEntry(SearchResult result) {
        return String.format("""
            ### %s
            **Source:** `%s`
            **Relevance:** %s (%.2f)

            %s

            """,
            result.section(),
            result.source(),
            result.confidence(),
            result.relevanceScore(),
            result.content()
        );
    }

    private static int estimateTokens(String text) {
        // Approximation: ~4 chars per token
        return text.length() / 4;
    }

    /**
     * Format erreur avec pattern isError: true.
     */
    public static CallToolResult errorResult(String message, ErrorCategory category) {
        return CallToolResult.builder()
            .isError(true)
            .addTextContent(String.format("""
                ## Error: %s

                **Problem:** %s

                **Suggested action:** %s
                """,
                category.title(),
                message,
                category.suggestedAction()))
            .build();
    }
}
```

**Notes:**
- `isError: true` → Visible par le LLM, permet de différencier erreurs métier
- Truncation explicite avec compteur de résultats restants
- Metadata inline (score, source) pour contexte immédiat
