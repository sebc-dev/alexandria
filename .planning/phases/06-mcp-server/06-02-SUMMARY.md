---
phase: 06-mcp-server
plan: 02
subsystem: api
tags: [spring-ai, mcp, tools, dto, claude-code]

# Dependency graph
requires:
  - phase: 06-01
    provides: MCP foundation (Spring AI dependency, STDIO profile, DocumentRepository methods)
provides:
  - Four MCP tools (search_docs, index_docs, list_categories, get_doc)
  - MCP response DTOs optimized for LLM consumption
  - Claude Code .mcp.json configuration file
affects: [07-documentation, end-user usage]

# Tech tracking
tech-stack:
  added: []
  patterns: [MCP tools with @Tool annotation, DTO mapping for API layer]

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/api/mcp/AlexandriaTools.java
    - src/main/java/fr/kalifazzia/alexandria/api/mcp/dto/SearchResultDto.java
    - src/main/java/fr/kalifazzia/alexandria/api/mcp/dto/DocumentDto.java
    - src/main/java/fr/kalifazzia/alexandria/api/mcp/dto/IndexResultDto.java
    - .mcp.json
  modified: []

key-decisions:
  - "DTOs use String for UUIDs and timestamps for cleaner JSON"
  - "Tool names use snake_case per MCP convention"
  - "HybridSearchFilters.withFilters() factory for default RRF parameters"
  - ".mcp.json uses relative JAR path (run from project root)"

patterns-established:
  - "DTO layer between domain and MCP API for clean serialization"
  - "Thin facade pattern: tools delegate to existing services"
  - "Input validation with helpful error messages in tools"

# Metrics
duration: 2min
completed: 2026-01-20
---

# Phase 06 Plan 02: MCP Tools Summary

**Four MCP tools (search_docs, index_docs, list_categories, get_doc) with DTOs and Claude Code .mcp.json configuration**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-20T18:16:45Z
- **Completed:** 2026-01-20T18:18:29Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Created three DTO records for MCP response serialization (SearchResultDto, DocumentDto, IndexResultDto)
- Implemented AlexandriaTools component with four @Tool annotated methods
- Created .mcp.json for Claude Code auto-discovery and integration
- All tools delegate to existing services (SearchService, IngestionService, DocumentRepository)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create MCP response DTOs** - `a34da2f` (feat)
2. **Task 2: Implement AlexandriaTools with @Tool annotations** - `2b844e0` (feat)
3. **Task 3: Create .mcp.json for Claude Code integration** - `fed0f8a` (chore)

## Files Created/Modified
- `src/main/java/fr/kalifazzia/alexandria/api/mcp/dto/SearchResultDto.java` - Flat DTO for search results with document metadata
- `src/main/java/fr/kalifazzia/alexandria/api/mcp/dto/DocumentDto.java` - Full document details DTO
- `src/main/java/fr/kalifazzia/alexandria/api/mcp/dto/IndexResultDto.java` - Index operation result DTO
- `src/main/java/fr/kalifazzia/alexandria/api/mcp/AlexandriaTools.java` - MCP tools facade with four tools
- `.mcp.json` - Claude Code MCP server configuration

## Decisions Made
- **String IDs in DTOs**: UUIDs serialized as strings for cleaner JSON output
- **String timestamps**: ISO format strings instead of Instant for easy LLM parsing
- **Comma-separated tags parameter**: Simple string input vs array for MCP tool simplicity
- **snake_case tool names**: Follows MCP convention (search_docs not searchDocs)
- **Thin facade pattern**: Tools validate input and delegate to services, no business logic

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

To use Alexandria with Claude Code:
1. Build JAR: `mvn package -DskipTests`
2. Start PostgreSQL: `docker compose up -d`
3. Claude Code will auto-detect .mcp.json and offer to enable the server

## Next Phase Readiness
- MCP server implementation complete
- All four tools functional (search_docs, index_docs, list_categories, get_doc)
- Ready for Phase 07 documentation or direct usage
- Requirements MCP-01, MCP-02, MCP-03, MCP-04 satisfied

---
*Phase: 06-mcp-server*
*Completed: 2026-01-20*
