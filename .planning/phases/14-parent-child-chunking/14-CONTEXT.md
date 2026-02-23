# Phase 14: Parent-Child Chunking - Context

**Gathered:** 2026-02-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Search returns complete context (code + surrounding prose) by linking child chunks to their parent sections. The chunker produces parent chunks (full H2/H3 sections) and child chunks (individual blocks) with parent-child links in metadata. When a child matches a query, search returns the parent's full content. jqwik property-based tests verify chunker invariants.

</domain>

<decisions>
## Implementation Decisions

### Parent granularity
- H2 and H3 headings define parents (two-level hierarchy)
- H3 = sub-parent (natural semantic unit: concept + code example)
- H2 = top-level parent; becomes direct parent of its blocks only when it has no H3 children
- If a H2 contains H3 sections, each H3 is a sub-parent with its own children; content directly under H2 (before first H3) creates children of the H2 parent

### Child granularity
- Each Markdown block = one child: paragraph, fenced code block, table, list
- Maximum granularity for vector matching precision (small-to-big retrieval pattern)
- The child serves as a "pointer" to the parent — the parent provides the full context

### H4+ headings
- H4 and deeper headings are treated as content within their H3 parent — no third hierarchy level
- The heading text is included in the child's text content
- section_path may include H4 for precision but no separate parent is created

### Short H3 sections
- Every H3 creates a parent regardless of size — no minimum threshold
- Avoids arbitrary thresholds and keeps section_path complete
- Duplication (parent ~ child when section is tiny) is handled by search deduplication

### Long H2 sections
- No size limit on parent content — store the full section text
- Size control is the search service's responsibility, not the chunker's
- Rationale: truncation at storage is destructive and irreversible; truncation at retrieval is adjustable

### Preamble content (before first heading)
- Content before the first heading creates a "root" parent
- section_path uses the page title (H1 or source name) or remains empty
- Same parent-child treatment as heading-based sections

### Claude's Discretion
- Storage mechanism for parent-child links (UUID reference, metadata key format, separate table vs JSONB)
- Parent identifier format in child metadata
- Additional metadata fields (chunk_type, position, hierarchy depth)
- Whether parents are embedded/indexed for search or stored as context-only
- Deduplication strategy when multiple children of same parent match
- Search result format changes (substitute child with parent, or return both)
- Reranker target (score child vs parent text)
- Migration strategy for existing chunks without parent links
- jqwik invariants selection and prioritization
- Property test scope (new parent-child invariants + legacy chunker invariants)
- Markdown generator complexity for property tests
- Whether to also test with real production Markdown samples

</decisions>

<specifics>
## Specific Ideas

- The pattern is "small-to-big retrieval": small chunks for precision matching, big chunks (parents) for context richness
- Parent chunks are not necessarily embedded — they can serve purely as context containers retrieved via child metadata links
- The current chunker already extracts code blocks separately — parent-child extends this existing behavior by adding hierarchy links
- Evaluation framework from Phase 13 should measure the impact of chunking changes on search quality

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 14-parent-child-chunking*
*Context gathered: 2026-02-22*
