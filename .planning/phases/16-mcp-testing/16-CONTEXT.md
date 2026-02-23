# Phase 16: MCP Testing - Context

**Gathered:** 2026-02-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Verify MCP tool contracts with snapshot tests (schema frozen against a reference file) and round-trip integration tests (JSON-RPC communication via McpClient against a live Spring Boot instance). The 7 existing MCP tools are tested as-is — no new tools or tool modifications in this phase.

</domain>

<decisions>
## Implementation Decisions

### Snapshot scope & format
- Capture the **complete tools/list schema**: tool names, parameters (name, type, required), descriptions, and full inputSchema
- **Tools only** — do not snapshot server metadata (name, version)
- Reference file: **JSON sorted alphabetically by keys, 2-space indent** for readable Git diffs
- Location: `src/test/resources/mcp/tools-schema.json`
- **Assertion on tool count** (7) as an explicit guard before JSON comparison

### Round-trip coverage
- **Happy path + key error cases** per tool: 1-2 happy paths + 1-2 error scenarios each (~20-25 tests total)
- **Uniform coverage** across all 7 tools — no tool is deprioritized
- Assertions verify **structure + key values**: response is well-formed (no JSON-RPC error) AND expected values present (e.g., search returns results, list_sources contains seeded source)
- Transport: **SSE / HTTP in-process** — Spring Boot on random port, McpClient connects via SSE using `web` profile

### Test data strategy
- **SQL seed script** in `src/test/resources/` inserts sources + chunks before the test suite — no dependency on Crawl4AI or network
- **Testcontainers pgvector** (`pgvector/pgvector:pg17` with `@ServiceConnection`) — same infra as existing integration tests
- **Real pre-computed embeddings** (BGE bge-small-en-v1.5-q, 384 dimensions) stored in the seed SQL for realistic vector search results
- **5-10 chunks across 2-3 sources** — enough to test filters (by source, contentType) and get search results, small enough to keep the seed readable

### Schema evolution workflow
- **Gradle task** (`updateMcpSnapshot` or similar) regenerates the reference file — dev sees diff in Git and commits explicitly
- Snapshot test failure message shows a **readable diff** (key-by-key: added, removed, modified) not raw JSON dump
- Snapshot test runs as a **unit test** (no Spring context needed) — fast, runs with `./quality.sh test`

### Claude's Discretion
- Exact Gradle task implementation for snapshot update
- McpClient configuration details (timeouts, retry)
- SQL seed script structure and exact chunk content
- Test class organization (one class per concern vs grouped)
- Diff formatting library choice (JSONAssert, custom, etc.)

</decisions>

<specifics>
## Specific Ideas

- Snapshot test as unit test keeps the feedback loop fast — devs see breakage on every `./quality.sh test` run, not just during slow integration test suites
- Pre-computed embeddings in the seed SQL avoid the need to load the ONNX model in tests that don't need it, while still producing realistic search results in round-trip tests

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 16-mcp-testing*
*Context gathered: 2026-02-23*
