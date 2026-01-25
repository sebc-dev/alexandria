# Phase 16: Monitoring Stack - Research

**Researched:** 2026-01-25
**Domain:** Observability stack with VictoriaMetrics, Grafana, Loki, and Alloy
**Confidence:** HIGH

## Summary

This phase adds a complete observability stack to the Alexandria project using Docker Compose profiles. The stack collects metrics from the Spring Boot application (via the `/actuator/prometheus` endpoint configured in Phase 15) and aggregates logs from all containers.

The research clarifies an important decision point: **Use Grafana Alloy instead of Promtail**. Promtail was deprecated in February 2025 and entered Long-Term Support with end-of-life scheduled for March 2026. Grafana Alloy is the official successor, providing unified telemetry collection for logs, metrics, and traces.

The recommended architecture uses:
- **VictoriaMetrics** (single-node) as the Prometheus-compatible metrics store
- **Grafana** with provisioned dashboards and datasources
- **Loki** for log aggregation
- **Grafana Alloy** for collecting Docker container logs

**Primary recommendation:** Use Docker Compose `profiles: ["eval"]` to make the monitoring stack optional, with all services having health checks and `depends_on: condition: service_healthy` for proper startup ordering.

## Standard Stack

The established tools for this observability domain:

### Core
| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| VictoriaMetrics | 1.114.0+ | Metrics storage | Prometheus-compatible, lower resource usage, single binary |
| Grafana | 11.x | Visualization & alerting | Industry standard, provisioning-as-code support |
| Loki | 3.4.0+ | Log aggregation | Grafana-native, label-based like Prometheus |
| Grafana Alloy | 1.x | Log collection | Replaces deprecated Promtail, unified telemetry agent |

### Supporting
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| vmalert | 1.114.0+ | VictoriaMetrics alerting | When using VM-native alerts instead of Grafana |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| VictoriaMetrics | Prometheus | Prometheus uses more RAM, requires separate long-term storage |
| Grafana Alloy | Promtail | Promtail deprecated Feb 2025, EOL March 2026 |
| Loki | Elasticsearch | Elasticsearch more complex, higher resource needs |

**Docker Images:**
```yaml
victoriametrics/victoria-metrics:v1.114.0
grafana/grafana:11.6.0
grafana/loki:3.4.0
grafana/alloy:1.8.0
```

## Architecture Patterns

### Recommended Directory Structure
```
monitoring/
├── grafana/
│   ├── provisioning/
│   │   ├── dashboards/
│   │   │   ├── dashboards.yaml       # Dashboard provider config
│   │   │   └── alexandria-rag.json   # RAG-specific dashboard
│   │   ├── datasources/
│   │   │   └── datasources.yaml      # VM + Loki datasources
│   │   └── alerting/
│   │       └── alerts.yaml           # Alert rules
│   └── grafana.ini                   # Optional custom config
├── victoriametrics/
│   └── scrape.yaml                   # Prometheus scrape config
├── loki/
│   └── loki-config.yaml              # Loki configuration
└── alloy/
    └── config.alloy                  # Alloy HCL configuration
```

### Pattern 1: Docker Compose Profiles for Optional Services
**What:** Use `profiles` attribute to make monitoring services optional
**When to use:** When services should only start with explicit profile activation
**Example:**
```yaml
# Source: https://docs.docker.com/compose/how-tos/profiles/
services:
  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.114.0
    profiles: ["eval"]
    # ... config

  grafana:
    image: grafana/grafana:11.6.0
    profiles: ["eval"]
    depends_on:
      victoriametrics:
        condition: service_healthy
    # ... config
```

**Usage:**
```bash
# Start without monitoring
docker compose up -d

# Start with monitoring
docker compose --profile eval up -d
```

### Pattern 2: Health Checks with service_healthy Condition
**What:** Define health checks and use `depends_on: condition: service_healthy`
**When to use:** Always for service dependencies requiring readiness
**Example:**
```yaml
# Source: https://docs.docker.com/compose/how-tos/startup-order/
services:
  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.114.0
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8428/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  grafana:
    depends_on:
      victoriametrics:
        condition: service_healthy
      loki:
        condition: service_healthy
```

### Pattern 3: Grafana Provisioning as Code
**What:** Auto-configure datasources, dashboards, and alerts via YAML files
**When to use:** Always for reproducible Grafana configuration
**Example:**
```yaml
# Source: https://grafana.com/docs/grafana/latest/administration/provisioning/
# provisioning/datasources/datasources.yaml
apiVersion: 1
datasources:
  - name: VictoriaMetrics
    type: prometheus
    access: proxy
    url: http://victoriametrics:8428
    isDefault: true
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
```

### Anti-Patterns to Avoid
- **Manual Grafana configuration:** Never configure datasources/dashboards via UI in containerized setup; use provisioning
- **Using Promtail:** Deprecated; use Grafana Alloy instead
- **Hardcoded IPs:** Use Docker service names (e.g., `victoriametrics:8428` not `172.x.x.x:8428`)
- **Missing health checks:** Always define health checks for `service_healthy` conditions to work
- **Curl in health checks:** VictoriaMetrics and Loki images don't include curl; use wget

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Metrics storage | Custom DB | VictoriaMetrics | Prometheus-compatible, efficient compression |
| Log collection | Custom scripts | Grafana Alloy | Handles Docker socket, log rotation, backpressure |
| Dashboard config | UI clicks | Grafana provisioning | Reproducible, version-controlled |
| Alert rules | Manual setup | Grafana alerting provisioning | Code-reviewable, consistent across envs |
| Scrape config | Manual curl | VictoriaMetrics scrape config | Handles retries, service discovery |

**Key insight:** The Grafana ecosystem provides provisioning-as-code for everything. Never manually configure observability tools via UI.

## Common Pitfalls

### Pitfall 1: Loki 3.6.0+ Health Check Changes
**What goes wrong:** Health checks fail with 404 after upgrading Loki
**Why it happens:** Loki 3.6.0 changed base image, removed busybox tools
**How to avoid:** Use Loki 3.4.0 or configure proper health check
**Warning signs:** Container restart loops, 404 on `/ready`

```yaml
# Working health check for Loki 3.4.0
healthcheck:
  test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3100/ready || exit 1"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 30s
```

### Pitfall 2: Alloy Ready Endpoint During Startup
**What goes wrong:** Alloy health checks fail during component initialization
**Why it happens:** `/-/ready` endpoint unavailable until all components initialize
**How to avoid:** Use generous `start_period` in health check
**Warning signs:** Alloy container restarts during startup

```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:12345/-/ready"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 120s  # Allow time for component initialization
```

### Pitfall 3: Docker Socket Permissions
**What goes wrong:** Alloy can't read container logs
**Why it happens:** Docker socket not mounted or wrong permissions
**How to avoid:** Mount `/var/run/docker.sock` to Alloy container
**Warning signs:** "0 targets" in Alloy, no logs in Loki

```yaml
alloy:
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock:ro
```

### Pitfall 4: Service Name Resolution
**What goes wrong:** Grafana can't connect to VictoriaMetrics or Loki
**Why it happens:** Using `localhost` instead of Docker service names
**How to avoid:** Use service names in datasource URLs
**Warning signs:** "Connection refused" errors in Grafana

```yaml
# WRONG
url: http://localhost:8428

# CORRECT
url: http://victoriametrics:8428
```

### Pitfall 5: Provisioned Resources Not Editable
**What goes wrong:** Can't modify provisioned dashboards/alerts in UI
**Why it happens:** Grafana prevents editing file-provisioned resources
**How to avoid:** Edit the source YAML files, restart Grafana
**Warning signs:** "Cannot save changes to provisioned dashboard" error

## Code Examples

### VictoriaMetrics Scrape Configuration
```yaml
# Source: https://docs.victoriametrics.com/victoriametrics/scrape_config_examples/
# monitoring/victoriametrics/scrape.yaml
scrape_configs:
  - job_name: 'alexandria'
    scrape_interval: 15s
    static_configs:
      - targets:
        - app:8080
    metrics_path: /actuator/prometheus

  - job_name: 'victoriametrics'
    scrape_interval: 15s
    static_configs:
      - targets:
        - localhost:8428
```

### Grafana Datasource Provisioning
```yaml
# Source: https://grafana.com/docs/grafana/latest/administration/provisioning/
# monitoring/grafana/provisioning/datasources/datasources.yaml
apiVersion: 1
datasources:
  - name: VictoriaMetrics
    type: prometheus
    access: proxy
    url: http://victoriametrics:8428
    isDefault: true
    editable: false
  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: false
```

### Grafana Dashboard Provider
```yaml
# Source: https://grafana.com/docs/grafana/latest/administration/provisioning/
# monitoring/grafana/provisioning/dashboards/dashboards.yaml
apiVersion: 1
providers:
  - name: 'Alexandria'
    orgId: 1
    folder: 'Alexandria'
    type: file
    disableDeletion: true
    editable: false
    options:
      path: /etc/grafana/provisioning/dashboards
```

### Grafana Alert Rule Provisioning
```yaml
# Source: https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/
# monitoring/grafana/provisioning/alerting/alerts.yaml
apiVersion: 1
groups:
  - orgId: 1
    name: Alexandria Alerts
    folder: Alexandria
    interval: 60s
    rules:
      - uid: alexandria-search-latency
        title: High Search Latency
        condition: C
        data:
          - refId: A
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: victoriametrics
            model:
              expr: histogram_quantile(0.95, sum(rate(alexandria_search_duration_seconds_bucket[5m])) by (le))
              refId: A
          - refId: B
            relativeTimeRange:
              from: 300
              to: 0
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: gt
                    params: [2]  # 2 seconds threshold
              refId: B
          - refId: C
            datasourceUid: __expr__
            model:
              type: classic_conditions
              conditions:
                - evaluator:
                    type: gt
                    params: [0]
                  query:
                    params: [B]
              refId: C
        for: 60s
        noDataState: NoData
        execErrState: Error
        annotations:
          summary: Search latency P95 exceeds 2 seconds
```

### Grafana Alloy Configuration for Docker Logs
```hcl
// Source: https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.docker/
// monitoring/alloy/config.alloy

// Discover Docker containers
discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
}

// Relabel to add container name
discovery.relabel "docker_logs" {
  targets = discovery.docker.containers.targets

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/(.*)"
    target_label  = "container"
  }

  rule {
    source_labels = ["__meta_docker_container_label_com_docker_compose_service"]
    target_label  = "service"
  }
}

// Collect logs from Docker containers
loki.source.docker "default" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.docker.containers.targets
  labels     = {"source" = "docker"}
  relabel_rules = discovery.relabel.docker_logs.rules
  forward_to = [loki.write.local.receiver]
}

// Send logs to Loki
loki.write "local" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

### Loki Configuration
```yaml
# Source: https://grafana.com/docs/loki/latest/setup/install/docker/
# monitoring/loki/loki-config.yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  ingestion_burst_size_mb: 16

query_scheduler:
  max_outstanding_requests_per_tenant: 2048
```

### Complete Docker Compose Services
```yaml
# Source: Compilation from official docs
services:
  # ... existing services (postgres, app) ...

  victoriametrics:
    image: victoriametrics/victoria-metrics:v1.114.0
    container_name: alexandria-vm
    profiles: ["eval"]
    networks:
      - internal
    volumes:
      - vm-data:/victoria-metrics-data
      - ./monitoring/victoriametrics/scrape.yaml:/etc/vm/scrape.yaml:ro
    command:
      - "-storageDataPath=/victoria-metrics-data"
      - "-retentionPeriod=30d"
      - "-promscrape.config=/etc/vm/scrape.yaml"
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8428/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s
    restart: unless-stopped

  loki:
    image: grafana/loki:3.4.0
    container_name: alexandria-loki
    profiles: ["eval"]
    networks:
      - internal
    volumes:
      - loki-data:/loki
      - ./monitoring/loki/loki-config.yaml:/etc/loki/config.yaml:ro
    command: -config.file=/etc/loki/config.yaml
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:3100/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped

  alloy:
    image: grafana/alloy:1.8.0
    container_name: alexandria-alloy
    profiles: ["eval"]
    networks:
      - internal
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./monitoring/alloy/config.alloy:/etc/alloy/config.alloy:ro
    command:
      - run
      - /etc/alloy/config.alloy
      - --server.http.listen-addr=0.0.0.0:12345
    depends_on:
      loki:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:12345/-/ready"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s
    restart: unless-stopped

  grafana:
    image: grafana/grafana:11.6.0
    container_name: alexandria-grafana
    profiles: ["eval"]
    networks:
      - internal
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      victoriametrics:
        condition: service_healthy
      loki:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3000/api/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped

volumes:
  vm-data:
  loki-data:
  grafana-data:
```

## RAG-Specific Dashboard Panels

For a RAG system like Alexandria, the dashboard should include:

### Key Metrics Panels
| Panel | PromQL Query | Purpose |
|-------|-------------|---------|
| Search Latency P95 | `histogram_quantile(0.95, sum(rate(alexandria_search_duration_seconds_bucket[5m])) by (le))` | Track user-facing latency |
| Search Latency P50 | `histogram_quantile(0.50, sum(rate(alexandria_search_duration_seconds_bucket[5m])) by (le))` | Typical response time |
| Embedding Latency P95 | `histogram_quantile(0.95, sum(rate(alexandria_embedding_duration_seconds_bucket[5m])) by (le))` | Embedding generation time |
| Documents Ingested | `increase(alexandria_documents_ingested_total[1h])` | Ingestion throughput |
| Search Rate | `rate(alexandria_search_duration_seconds_count[5m])` | Requests per second |
| Error Rate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | Application errors |

### Recommended Dashboard Layout
1. **Row 1: Overview** - Current search rate, error rate, active connections
2. **Row 2: Latency** - P50/P95/P99 for search and embedding operations
3. **Row 3: Throughput** - Documents ingested, search queries over time
4. **Row 4: JVM Metrics** - Heap usage, GC time (from Spring Boot auto-metrics)
5. **Row 5: Logs** - Loki log panel filtered by service

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Promtail | Grafana Alloy | Feb 2025 | Unified telemetry agent |
| Prometheus | VictoriaMetrics | Ongoing | Lower resource usage, PromQL compatible |
| Manual Grafana config | Provisioning as code | Grafana 5+ | Reproducible deployments |
| Separate alert tools | Grafana Unified Alerting | Grafana 9+ | Single place for all alerts |

**Deprecated/outdated:**
- **Promtail**: Deprecated Feb 2025, EOL March 2026. Use Grafana Alloy.
- **Grafana Agent**: EOL November 2025. Use Grafana Alloy.
- **Loki 3.6.0 health checks**: Changed base image, may break wget health checks

## Open Questions

Things that couldn't be fully resolved:

1. **Loki version choice**
   - What we know: Loki 3.6.0 changed base image, breaking some health checks
   - What's unclear: Whether 3.4.0 has all needed features
   - Recommendation: Use 3.4.0 for stability; health checks work reliably

2. **Alert notification channels**
   - What we know: Grafana can send alerts to email, Slack, webhooks
   - What's unclear: Which notification channels to provision
   - Recommendation: Start with no notification channel (view in UI); add later based on needs

3. **Retention periods**
   - What we know: VictoriaMetrics default 1 month, Loki configurable
   - What's unclear: Appropriate retention for eval environment
   - Recommendation: 30 days for metrics, 7 days for logs (eval environment)

## Sources

### Primary (HIGH confidence)
- [VictoriaMetrics Scrape Config Examples](https://docs.victoriametrics.com/victoriametrics/scrape_config_examples/) - Scrape configuration
- [Grafana Provisioning Documentation](https://grafana.com/docs/grafana/latest/administration/provisioning/) - Dashboard/datasource provisioning
- [Grafana Alerting File Provisioning](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/) - Alert rule provisioning
- [Grafana Alloy loki.source.docker](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.docker/) - Docker log collection
- [Docker Compose Profiles](https://docs.docker.com/compose/how-tos/profiles/) - Optional service profiles
- [Docker Compose Startup Order](https://docs.docker.com/compose/how-tos/startup-order/) - depends_on with conditions

### Secondary (MEDIUM confidence)
- [Grafana Loki 3.4 Release Blog](https://grafana.com/blog/2025/02/13/grafana-loki-3.4-standardized-storage-config-sizing-guidance-and-promtail-merging-into-alloy/) - Promtail deprecation announcement
- [Migrate from Promtail to Alloy](https://grafana.com/docs/alloy/latest/set-up/migrate/from-promtail/) - Migration guidance
- [Loki Docker Installation](https://grafana.com/docs/loki/latest/setup/install/docker/) - Loki Docker setup

### Tertiary (LOW confidence)
- [VictoriaMetrics Docker Health Check Issue](https://github.com/VictoriaMetrics/VictoriaMetrics/issues/3539) - Community health check examples
- [Grafana Provisioning Alerting Examples](https://github.com/grafana/provisioning-alerting-examples) - Example configurations

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official documentation for all components
- Architecture: HIGH - Docker Compose patterns well-documented
- Health checks: HIGH - Verified endpoints from official docs
- Alerting provisioning: MEDIUM - Complex YAML structure, example-based
- RAG dashboard panels: MEDIUM - Based on Phase 15 metrics + general patterns

**Research date:** 2026-01-25
**Valid until:** 2026-03-25 (60 days - components are stable but Alloy is evolving)
