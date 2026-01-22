---
milestone: v1.1
started: 2026-01-22T15:35:00Z
completed: 2026-01-22T18:45:00Z
status: passed
---

# v1.1 Full Docker - User Acceptance Testing

## Test Summary

| # | Test | Status | Notes |
|---|------|--------|-------|
| 1 | Docker Compose starts both services | ✓ pass | |
| 2 | Both services healthy within 120s | ✓ pass | |
| 3 | Actuator health returns UP | ✓ pass | {"status":"UP","groups":["liveness","readiness"]} |
| 4 | SSE endpoint responds | ✓ pass | HTTP 200, text/event-stream |
| 5 | Container runs as non-root | ✓ pass | uid=10000(alexandria) |
| 6 | CLI wrapper shows help | ✓ pass | |
| 7 | CLI indexes documents | ✓ pass | 8 docs, 234 chunks in 0.4s |
| 8 | CLI search returns results | ✓ pass | 5 results for "Alexandria" |
| 9 | Graceful shutdown completes | ✓ pass | 1.7s |

**Result: 9/9 passed**

## Issues Found and Fixed

### Issue 1: CLI port conflict
- **Symptom:** `./alexandria index` failed with "Port 8080 was already in use"
- **Root cause:** CLI profile didn't disable web server
- **Fix:** Added `spring.main.web-application-type: none` to cli profile

### Issue 2: Command not found
- **Symptom:** `No command found for 'status'`
- **Root cause:** Missing `@CommandScan` annotation on Application class
- **Fix:** Added `@CommandScan` to enable Spring Shell command discovery

### Issue 3: spring-shell.log AccessDeniedException
- **Symptom:** Warning on shutdown about /application/spring-shell.log
- **Root cause:** Spring Shell tried to save history to non-writable directory
- **Fix:** Added `spring.shell.history.enabled: false` for cli profile

## Test Details

### Test 1: Docker Compose starts both services
**Command:** `docker compose up -d`
**Expected:** Both postgres and alexandria-app containers start without errors
**Status:** ✓ pass

### Test 2: Both services healthy within 120s
**Command:** `docker compose ps`
**Expected:** Both services show "healthy" status
**Status:** ✓ pass

### Test 3: Actuator health returns UP
**Command:** `curl http://localhost:8080/actuator/health`
**Expected:** `{"status":"UP",...}`
**Result:** `{"status":"UP","groups":["liveness","readiness"]}`
**Status:** ✓ pass

### Test 4: SSE endpoint responds
**Command:** `curl -v http://localhost:8080/sse 2>&1 | head -20`
**Expected:** HTTP 200 with Content-Type: text/event-stream
**Result:** HTTP 200, Content-Type: text/event-stream
**Status:** ✓ pass

### Test 5: Container runs as non-root
**Command:** `docker exec alexandria-app id`
**Expected:** uid=10000(alexandria)
**Result:** uid=10000(alexandria) gid=10000(alexandria) groups=10000(alexandria)
**Status:** ✓ pass

### Test 6: CLI wrapper shows help
**Command:** `./alexandria`
**Expected:** Help message with available commands
**Result:** Help displayed with index, search, status, clear commands
**Status:** ✓ pass

### Test 7: CLI indexes documents
**Command:** `./alexandria index --path /docs`
**Expected:** Documents indexed without errors
**Result:** Indexing completed in 0.4 seconds. Documents: 8, Chunks: 234
**Status:** ✓ pass

### Test 8: CLI search returns results
**Command:** `./alexandria search --query "Alexandria"`
**Expected:** Search results displayed
**Result:** Found 5 results with scores and excerpts
**Status:** ✓ pass

### Test 9: Graceful shutdown completes
**Command:** `time docker compose down`
**Expected:** Completes in <10s (no SIGKILL)
**Result:** 1.727 total
**Status:** ✓ pass

---
*Started: 2026-01-22T15:35:00Z*
*Completed: 2026-01-22T18:45:00Z*
*Tester: Human + Claude*
