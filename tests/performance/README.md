# Performance Testing - Alexandria

Performance test suite for validating Alexandria's NFR requirements (NFR1-NFR6) using k6 load testing.

## 📋 Overview

This test suite validates:

- **NFR1:** End-to-end retrieval p50 ≤3s, p95 ≤5s, p99 ≤10s
- **NFR2:** Layer 1 vector search ≤1s (95%)
- **NFR3:** Layer 2 SQL joins ≤500ms (95%)
- **NFR4:** Layer 3 LLM reformulation ≤2s (95%)
- **NFR6:** 5+ concurrent requests without degradation

## 🚀 Quick Start

### Prerequisites

1. **Install k6:**
   ```bash
   # macOS
   brew install k6

   # Ubuntu/Debian
   sudo gpg -k
   sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
   echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
   sudo apt-get update
   sudo apt-get install k6

   # Windows (via Chocolatey)
   choco install k6
   ```

2. **Start Alexandria MCP Server:**
   ```bash
   # Ensure PostgreSQL + pgvector is running
   docker-compose up -d postgres

   # Start Alexandria
   bun run dev
   ```

3. **Seed Test Data (optional but recommended):**
   ```bash
   # Seed with 100 conventions
   bun run db:seed --count 100

   # Seed with 1,000 conventions
   bun run db:seed --count 1000

   # Seed with 10,000 conventions
   bun run db:seed --count 10000
   ```

### Running Tests

#### Individual Layer Tests

**Layer 1: Vector Search**
```bash
# Default (test environment)
k6 run layer1-vector-search.k6.js

# With custom base URL
k6 run --env ALEXANDRIA_BASE_URL=http://localhost:3000 layer1-vector-search.k6.js

# With specific project ID
k6 run --env ALEXANDRIA_PROJECT_ID=my-project layer1-vector-search.k6.js

# Smoke test (quick validation)
k6 run --vus 1 --duration 30s layer1-vector-search.k6.js
```

**Layer 2: SQL Joins**
```bash
# Default
k6 run layer2-sql-joins.k6.js

# Smoke test
k6 run --vus 1 --duration 30s layer2-sql-joins.k6.js
```

**End-to-End Pipeline**
```bash
# Full test (includes Layer 3 sub-agent)
k6 run end-to-end-retrieval.k6.js

# Quick validation
k6 run --vus 2 --duration 1m end-to-end-retrieval.k6.js
```

#### Baseline Benchmarking

Run these tests to establish baseline performance metrics:

```bash
# 1. Baseline with 100 embeddings
bun run db:seed --count 100
k6 run layer1-vector-search.k6.js > results/baseline-100-embeddings.txt

# 2. Baseline with 1,000 embeddings
bun run db:seed --count 1000
k6 run layer1-vector-search.k6.js > results/baseline-1k-embeddings.txt

# 3. Baseline with 10,000 embeddings
bun run db:seed --count 10000
k6 run layer1-vector-search.k6.js > results/baseline-10k-embeddings.txt

# 4. End-to-end baseline
k6 run end-to-end-retrieval.k6.js > results/baseline-e2e.txt
```

## 📊 Performance Targets (SLOs)

### NFR1: End-to-End Retrieval

| Metric | Target | Test |
|--------|--------|------|
| P50 latency | ≤3s | `end-to-end-retrieval.k6.js` |
| P95 latency | ≤5s | `end-to-end-retrieval.k6.js` |
| P99 latency | ≤10s | `end-to-end-retrieval.k6.js` |

### NFR2: Layer 1 Vector Search

| Metric | Target | Test |
|--------|--------|------|
| P95 latency | ≤1s | `layer1-vector-search.k6.js` |
| P50 latency | ≤500ms | `layer1-vector-search.k6.js` |
| Error rate | <1% | `layer1-vector-search.k6.js` |

### NFR3: Layer 2 SQL Joins

| Metric | Target | Test |
|--------|--------|------|
| P95 latency | ≤500ms | `layer2-sql-joins.k6.js` |
| P50 latency | ≤250ms | `layer2-sql-joins.k6.js` |
| Error rate | <1% | `layer2-sql-joins.k6.js` |

### NFR4: Layer 3 LLM Reformulation

| Metric | Target | Test |
|--------|--------|------|
| P95 latency | ≤2s | `end-to-end-retrieval.k6.js` (Layer 3 breakdown) |
| Token limit | <5000 tokens | `end-to-end-retrieval.k6.js` |

### NFR6: Concurrent Requests

| Metric | Target | Test |
|--------|--------|------|
| Concurrent VUs | ≥5 simultaneous | All tests (stages ramp to 50+ VUs) |
| Degradation | <10% at 5 VUs | Compare p95 at 1 VU vs 5 VUs |

## 🔍 Interpreting Results

### ✅ PASS Criteria

A test **PASSES** when:

1. All thresholds are met (green checkmarks in output)
2. Error rate < 1%
3. P95 latency meets or beats target
4. No timeout errors

### ⚠️ CONCERNS Criteria

A test is **CONCERNS** when:

1. P95 latency within 20% of threshold (e.g., 900ms for 1s target)
2. Error rate 0.5-1%
3. Occasional slow queries but no pattern

### ❌ FAIL Criteria

A test **FAILS** when:

1. P95 latency exceeds threshold
2. Error rate ≥1%
3. Frequent timeouts or connection errors

### Example Output Interpretation

```text
📊 Performance Metrics:
   • P50 Latency:    450ms ✅ (Target: <500ms)
   • P95 Latency:    920ms ✅ (Target: <1000ms)
   • P99 Latency:    1800ms
   • Error Rate:     0.3% ✅ (Target: <1%)

🎯 NFR2 Validation:
   ✅ PASS - Vector search p95 latency meets 1s threshold
```

**Interpretation:** Test PASSES. P95 is 920ms (within threshold), error rate is acceptable.

## 📈 Baseline Documentation

After running baseline tests, document results in `docs/performance-baselines.md`:

```markdown
## Performance Baseline - 2025-12-27

### Test Environment
- PostgreSQL: 17.7 + pgvector 0.8.1
- Embeddings: 10,000 conventions
- Hardware: MacBook Pro M1, 16GB RAM
- Network: localhost

### Results

#### Layer 1 Vector Search
- P50: 420ms ✅
- P95: 850ms ✅ (Target: <1s)
- P99: 1200ms
- Status: PASS

#### Layer 2 SQL Joins
- P50: 180ms ✅
- P95: 320ms ✅ (Target: <500ms)
- P99: 450ms
- Status: PASS

#### End-to-End Retrieval
- P50: 2100ms ✅ (Target: <3s)
- P95: 3800ms ✅ (Target: <5s)
- P99: 6500ms ✅ (Target: <10s)
- Status: PASS

### Analysis
All NFRs met. HNSW index (m=16, ef_construction=64) performs well up to 10K embeddings.
```

## 🛠️ Troubleshooting

### High Latency Issues

**Layer 1 (Vector Search) Slow:**
```bash
# Check HNSW index exists
psql -c "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'embeddings';"

# Run EXPLAIN ANALYZE
psql -c "EXPLAIN ANALYZE SELECT * FROM embeddings ORDER BY embedding <=> '[0.1,0.2,...]' LIMIT 5;"

# Consider tuning HNSW parameters
# In migration: CREATE INDEX ... USING hnsw (embedding vector_cosine_ops) WITH (m = 32, ef_construction = 128);
```

**Layer 2 (SQL Joins) Slow:**
```bash
# Verify indexes
psql -c "SELECT indexname FROM pg_indexes WHERE tablename IN ('convention_technologies', 'documentation_technologies');"

# Run EXPLAIN ANALYZE
psql -c "EXPLAIN ANALYZE SELECT d.* FROM documentation d JOIN documentation_technologies dt ON d.id = dt.documentation_id WHERE dt.technology_id IN (...);"

# Run VACUUM ANALYZE
psql -c "VACUUM ANALYZE convention_technologies; VACUUM ANALYZE documentation_technologies;"
```

**Layer 3 (LLM) Slow:**
- Check Haiku 4.5 quota (Plan Max limits)
- Reduce context size (fewer conventions/docs)
- Monitor sub-agent invocation logs

### Connection Errors

```bash
# Verify Alexandria is running
curl http://localhost:3000/health

# Check PostgreSQL connection
psql -h localhost -U postgres -c "SELECT version();"

# Check Docker containers
docker ps | grep postgres
```

### Error Rate >1%

```bash
# Check Alexandria logs
tail -f logs/alexandria-$(date +%Y-%m-%d).jsonl | jq 'select(.level == "error")'

# Check for database errors
psql -c "SELECT * FROM pg_stat_activity WHERE state = 'active';"
```

## 🔄 CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/performance-tests.yml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * *'  # Daily at 2 AM
  workflow_dispatch:

jobs:
  performance:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg17
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s

    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v1

      - name: Install k6
        run: |
          sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
          echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
          sudo apt-get update
          sudo apt-get install k6

      - name: Seed database
        run: bun run db:seed --count 1000

      - name: Run performance tests
        env:
          ALEXANDRIA_BASE_URL: http://localhost:3000
        run: |
          k6 run tests/performance/layer1-vector-search.k6.js --out json=results-layer1.json
          k6 run tests/performance/layer2-sql-joins.k6.js --out json=results-layer2.json
          k6 run tests/performance/end-to-end-retrieval.k6.js --out json=results-e2e.json

      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: performance-results
          path: results-*.json
```

## 📚 Additional Resources

- [k6 Documentation](https://k6.io/docs/)
- [pgvector HNSW Tuning Guide](https://github.com/pgvector/pgvector#hnsw)
- [PostgreSQL EXPLAIN ANALYZE](https://www.postgresql.org/docs/current/using-explain.html)
- Alexandria Performance Baselines: `docs/performance-baselines.md` (create after running tests)

## 🎯 Next Steps After Baseline

1. ✅ Run all baseline tests with 100/1K/10K embeddings
2. ✅ Document results in `docs/performance-baselines.md`
3. ✅ Add CI job for weekly performance regression testing
4. ✅ Set up alerts for performance degradation (p95 >threshold)
5. ✅ Update `test-design-system` to mark TC-006 as RESOLVED

---

**Generated by:** BMad TEA Agent - BLOCKER TC-006 Resolution
**Date:** 2025-12-27
**Status:** Ready for Sprint 0 Baseline Benchmarking
