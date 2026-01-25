---
phase: 16-monitoring-stack
plan: 02
subsystem: infra
tags: [loki, alloy, grafana, logging, docker, monitoring]

# Dependency graph
requires:
  - phase: 16-01
    provides: VictoriaMetrics and Grafana services with eval profile
provides:
  - Loki log aggregation with TSDB schema v13
  - Alloy Docker log collector forwarding to Loki
  - Loki datasource in Grafana for log exploration
affects: [16-03 alerting, eval runs, log analysis]

# Tech tracking
tech-stack:
  added: [grafana/loki:3.4.0, grafana/alloy:1.8.0]
  patterns: [Alloy discovery.docker for container log collection, Loki TSDB storage]

key-files:
  created:
    - monitoring/loki/loki-config.yaml
    - monitoring/alloy/config.alloy
  modified:
    - docker-compose.yml
    - monitoring/grafana/provisioning/datasources/datasources.yaml

key-decisions:
  - "Loki 3.4.0 for reliable health check compatibility"
  - "Alloy with Docker socket mount for container log discovery"
  - "Grafana depends on both VictoriaMetrics and Loki"

patterns-established:
  - "Alloy relabeling for container and service labels"
  - "Loki datasource alongside VictoriaMetrics in Grafana"

# Metrics
duration: 1.5min
completed: 2026-01-25
---

# Phase 16 Plan 02: Log Aggregation Stack Summary

**Loki 3.4.0 and Alloy 1.8.0 added for centralized Docker container log collection, queryable in Grafana**

## Performance

- **Duration:** 1.5 min
- **Started:** 2026-01-25T13:35:54Z
- **Completed:** 2026-01-25T13:37:23Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Loki log aggregation with TSDB schema and filesystem storage
- Alloy service collecting Docker container logs via socket
- Loki datasource provisioned in Grafana
- Proper service dependencies ensuring startup order

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Loki service with configuration** - `efc09d4` (feat)
2. **Task 2: Add Alloy service and update Grafana dependencies** - `fc33d60` (feat)

## Files Created/Modified
- `monitoring/loki/loki-config.yaml` - Loki server configuration with TSDB schema
- `monitoring/alloy/config.alloy` - Docker log discovery and forwarding to Loki
- `docker-compose.yml` - Added loki and alloy services with eval profile
- `monitoring/grafana/provisioning/datasources/datasources.yaml` - Added Loki datasource

## Decisions Made
- Used Loki 3.4.0 (not 3.6.0+) for reliable health check via wget
- Alloy uses generous 120s start_period for health check
- Alloy relabels container logs with container name and compose service labels

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Full monitoring stack ready: metrics (VictoriaMetrics) + logs (Loki)
- `docker compose --profile eval up` starts all 4 monitoring services
- Grafana at http://localhost:3000 has both datasources
- Ready for dashboard provisioning in next plan

---
*Phase: 16-monitoring-stack*
*Completed: 2026-01-25*
