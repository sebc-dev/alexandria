# Milestones

## v0.1 Full RAG System (Shipped: 2026-02-20)

**Phases completed:** 10 phases, 28 plans, 0 tasks

**Key accomplishments:**
- End-to-end RAG pipeline: crawl → chunk → embed → search via MCP stdio
- Hybrid search (vector + keyword + RRF) with cross-encoder reranking and rich filtering
- MCP Server (7 tools) integrating Claude Code for documentation search and source management
- Crawl4AI sidecar with incremental crawls, scope controls, sitemap/llms.txt discovery
- Markdown-aware chunking preserving code blocks, tables, heading hierarchy
- Full source lifecycle: add, list, remove (cascade delete), statistics, staleness indicators

**Stats:** 171 commits, 261 files, 9,839 LOC Java, 7 days (2026-02-14 → 2026-02-20)
**Requirements:** 41/42 satisfied (CRWL-07 deferred: manual recrawl vs scheduled)
**Tech debt:** 9 items (all Low/Info severity)

---

