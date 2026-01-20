package fr.kalifazzia.alexandria.api.mcp.dto;

import java.util.List;

/**
 * DTO for search result in MCP response.
 * Flat structure optimized for LLM consumption.
 */
public record SearchResultDto(
        String documentId,
        String documentTitle,
        String documentPath,
        String category,
        List<String> tags,
        String matchedContent,
        String parentContext,
        double similarity
) {}
