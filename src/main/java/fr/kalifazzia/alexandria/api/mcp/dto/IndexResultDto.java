package fr.kalifazzia.alexandria.api.mcp.dto;

/**
 * DTO for index operation result.
 */
public record IndexResultDto(
        String directoryPath,
        String status,
        String message
) {}
