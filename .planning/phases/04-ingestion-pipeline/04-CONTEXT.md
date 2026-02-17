# Phase 4: Ingestion Pipeline - Context

**Gathered:** 2026-02-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Transform crawled Markdown into richly-annotated, searchable chunks that preserve code block integrity and heading hierarchy. Two chunking modes: mechanical heading-based (default) and AI-assisted via external tools (Gemini CLI / Claude Code). End-to-end pipeline: crawl → chunk → embed → searchable.

</domain>

<decisions>
## Implementation Decisions

### Chunking strategy
- Heading-only splitting at H1, H2, and H3 boundaries
- H4+ remains within the parent H3 chunk
- No max size limit — each heading section = 1 chunk regardless of length
- Short chunks (1-2 sentences under a H3) are kept as-is — precision over size
- No textual overlap between chunks — heading path provides context instead
- AI-assisted chunking available as alternative mode (via pre-chunked JSON import)

### Code block handling
- Every fenced code block is extracted as a separate chunk with `content_type=code`
- Language tag preserved from the fence (```java → language=java)
- Blocks without language tag get `language=unknown` with best-effort auto-detection when possible
- Code chunks inherit the heading path of their parent section (no [code] suffix)
- The prose chunk retains its text minus the extracted code blocks

### Metadata enrichment
- 5 metadata fields per chunk: `source_url`, `section_path`, `content_type`, `last_updated`, `language`
- `section_path` uses slash separator: `guide/configuration/routes`
- `content_type` has 2 values: `prose` and `code`
- `language` field populated for code chunks (java, python, yaml, unknown, etc.); null for prose chunks
- Existing convention: metadata keys use snake_case (established in Phase 2)

### Pre-chunked content import
- Import via JSON file containing chunks with complete metadata
- JSON must include all 5 metadata fields per chunk — Alexandria stores as-is, no inference
- Validation: reject entire file if any chunk is invalid (all-or-nothing)
- Import mode: replacement — deletes existing chunks for the source_url before importing new ones
- Designed for AI-assisted workflow: external tool (Gemini CLI / Claude Code) produces optimized chunks, exports as JSON, user imports into Alexandria

### Claude's Discretion
- Markdown parsing library choice (flexmark-java or alternative)
- Code language auto-detection approach
- JSON schema design for pre-chunked import format
- Chunking implementation details (AST walking vs regex vs streaming)
- Error reporting format for JSON validation failures

</decisions>

<specifics>
## Specific Ideas

- "Je souhaite utiliser Heading Only mais aussi qu'il soit possible de faire les chunks assisté par IA via Gemini CLI ou Claude Code pour les faire de manière optimisée"
- Code blocks with unknown language should attempt detection — not just tag as unknown and leave it
- Rejet complet pour la validation JSON — la cohérence des données prime sur la commodité

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-ingestion-pipeline*
*Context gathered: 2026-02-17*
