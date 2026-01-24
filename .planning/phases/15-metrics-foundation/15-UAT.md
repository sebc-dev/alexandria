---
status: testing
phase: 15-metrics-foundation
source: 15-01-SUMMARY.md
started: 2026-01-24T08:15:00Z
updated: 2026-01-24T08:15:00Z
---

## Current Test

number: 1
name: Prometheus Endpoint Accessible
expected: |
  1. Start the application with Docker: `docker compose up -d && mvn spring-boot:run`
  2. Wait for startup (~10 seconds)
  3. Run: `curl -s http://localhost:8080/actuator/prometheus | head -20`
  4. Response shows Prometheus exposition format with `# HELP` and `# TYPE` lines
awaiting: user response

## Tests

### 1. Prometheus Endpoint Accessible
expected: Start application, curl /actuator/prometheus returns 200 with Prometheus format (# HELP, # TYPE lines)
result: [pending]

### 2. Search Latency Timer Present
expected: After running a search, `curl /actuator/prometheus | grep alexandria_search` shows `alexandria_search_duration_seconds` metric with histogram buckets
result: [pending]

### 3. Embedding Timer Present
expected: After ingesting a document, `curl /actuator/prometheus | grep alexandria_embedding` shows `alexandria_embedding_duration_seconds` metric with histogram buckets
result: [pending]

### 4. Document Ingestion Counter Present
expected: After ingesting a document, `curl /actuator/prometheus | grep alexandria_documents` shows `alexandria_documents_ingested_total` counter incremented
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0

## Gaps

[none yet]
