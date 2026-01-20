package fr.kalifazzia.alexandria.api.mcp.dto;

import java.util.List;
import java.util.Map;

/**
 * DTO for full document in MCP response.
 */
public record DocumentDto(
        String id,
        String path,
        String title,
        String category,
        List<String> tags,
        Map<String, Object> frontmatter,
        String createdAt,
        String updatedAt
) {}
