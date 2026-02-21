# Phase 12: Performance Quick Wins - Context

**Gathered:** 2026-02-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Configuration-only tuning to reduce latency and resource usage. No code refactoring. Covers: ONNX Runtime thread tuning, PostgreSQL config for RAG workload, HikariCP pool sizing for virtual threads, and BGE query prefix for embedding requests.

</domain>

<decisions>
## Implementation Decisions

### PostgreSQL Resource Allocation
- Machine: 24 GB RAM, 4 CPU cores
- Generous allocation (~6 GB for PostgreSQL)
- shared_buffers=2GB, maintenance_work_mem=1GB
- ef_search=100 (global, in postgresql.conf)
- JIT disabled globally (jit=off)

### PostgreSQL Config Delivery
- Mounted postgresql.conf file via docker-compose volume
- File location: docker/postgres/postgresql.conf
- Override-only file (only tuned params, PG defaults for the rest)
- ef_search set in postgresql.conf (global, not per-session)

### ONNX Runtime Thread Pools
- allow_spinning=0 (disable thread spinning to reduce idle CPU)
- Aggressive allocation: 4 intra-op threads / 2 inter-op threads
- Configure via application.properties (if LangChain4j/Spring exposes these; fallback to Java SessionOptions @Bean)
- Single config for both query and ingestion contexts

### HikariCP Connection Pool
- Pool size: 10 connections
- Connection timeout: 5 seconds (fail fast for interactive MCP usage)
- Leak detection threshold: 30 seconds (log warning for held connections)
- max-lifetime and idle-timeout: HikariCP defaults (30min / 10min)

### Claude's Discretion
- BGE query prefix implementation approach (where in the code path to inject "Represent this sentence: " prefix)
- Exact work_mem and effective_cache_size values in postgresql.conf
- ONNX config fallback strategy if application.properties doesn't expose thread settings

</decisions>

<specifics>
## Specific Ideas

- PostgreSQL is the sole consumer of the database — no need for per-session configs
- Alexandria is self-hosted with low concurrency — conservative pool sizing is fine
- Virtual threads share connections efficiently — smaller pool is better than oversized

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 12-performance-quick-wins*
*Context gathered: 2026-02-21*
