/**
 * End-to-End Performance Test: Complete Retrieval Pipeline
 *
 * Tests NFR1: Retrieval p50 ≤3s, p95 ≤5s, p99 ≤10s end-to-end
 *
 * Validates:
 * - Layer 1: Vector search (≤1s target)
 * - Layer 2: SQL joins (≤500ms target)
 * - Layer 3: LLM reformulation (≤2s target)
 * - Total pipeline latency
 *
 * SLO Targets: p50 <3s, p95 <5s, p99 <10s
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const e2eLatency = new Trend('e2e_total_latency');
const layer1Latency = new Trend('e2e_layer1_latency');
const layer2Latency = new Trend('e2e_layer2_latency');
const layer3Latency = new Trend('e2e_layer3_latency');
const errorRate = new Rate('e2e_errors');
const retrievalCount = new Counter('e2e_retrievals');
const contextSize = new Gauge('e2e_context_tokens');

// Test configuration
export const options = {
  stages: [
    { duration: '1m', target: 5 },    // Warm-up: 5 concurrent requests
    { duration: '2m', target: 10 },   // Ramp up to 10 concurrent
    { duration: '3m', target: 10 },   // Sustain 10 concurrent
    { duration: '1m', target: 20 },   // Spike to 20 concurrent
    { duration: '2m', target: 20 },   // Sustain spike
    { duration: '1m', target: 0 },    // Ramp down
  ],
  thresholds: {
    // NFR1: End-to-end retrieval SLOs
    'e2e_total_latency': ['p(50)<3000', 'p(95)<5000', 'p(99)<10000'],
    // Component latencies
    'e2e_layer1_latency': ['p(95)<1000'],   // NFR2
    'e2e_layer2_latency': ['p(95)<500'],    // NFR3
    'e2e_layer3_latency': ['p(95)<2000'],   // NFR4
    // Reliability
    'e2e_errors': ['rate<0.01'], // <1% error rate
    'http_req_duration': ['p(95)<5000'],
  },
};

// Test data: Realistic code generation queries
const codeGenerationQueries = [
  'How to implement a repository pattern with Drizzle ORM for PostgreSQL?',
  'Best practices for validating API inputs with Zod in TypeScript?',
  'How to structure a hexagonal architecture with TypeScript and Bun?',
  'Implementing HNSW vector search with pgvector in PostgreSQL?',
  'How to handle errors gracefully in a Hono MCP server?',
  'Setting up dependency injection without IoC containers in TypeScript?',
  'Creating immutable value objects with TypeScript readonly properties?',
  'How to test integration with PostgreSQL using test containers?',
  'Implementing multi-project isolation with application-level filtering?',
  'How to orchestrate Claude Code sub-agents for LLM tasks?',
];

export default function () {
  const baseUrl = __ENV.ALEXANDRIA_BASE_URL || 'http://localhost:3000';
  const projectId = __ENV.ALEXANDRIA_PROJECT_ID || 'test-project';

  // Select random query
  const query = codeGenerationQueries[Math.floor(Math.random() * codeGenerationQueries.length)];

  // Prepare request payload (simulating Skill Alexandria invocation)
  const payload = JSON.stringify({
    query: query,
    projectId: projectId,
    includeReformulation: true, // Enable Layer 3
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '15s', // Allow time for Layer 3 sub-agent
  };

  // Execute end-to-end retrieval
  const startTime = Date.now();
  const response = http.post(`${baseUrl}/api/retrieve/context`, payload, params);
  const totalDuration = Date.now() - startTime;

  // Record total latency
  e2eLatency.add(totalDuration);
  retrievalCount.add(1);

  // Parse response to extract layer latencies
  let layerMetrics = { layer1: 0, layer2: 0, layer3: 0 };
  let contextTokens = 0;

  try {
    const body = JSON.parse(response.body);

    if (body.metrics) {
      layerMetrics = {
        layer1: body.metrics.layer1Ms || 0,
        layer2: body.metrics.layer2Ms || 0,
        layer3: body.metrics.layer3Ms || 0,
      };

      layer1Latency.add(layerMetrics.layer1);
      layer2Latency.add(layerMetrics.layer2);
      layer3Latency.add(layerMetrics.layer3);
    }

    if (body.context && body.context.tokenCount) {
      contextTokens = body.context.tokenCount;
      contextSize.add(contextTokens);
    }
  } catch (e) {
    // Continue even if parsing fails
  }

  // Validate response
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'total latency <3s (p50 target)': (r) => totalDuration < 3000,
    'total latency <5s (p95 target)': (r) => totalDuration < 5000,
    'total latency <10s (p99 target)': (r) => totalDuration < 10000,
    'response has context': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.context && typeof body.context.unified === 'string';
      } catch (e) {
        return false;
      }
    },
    'Layer 1 <1s': () => layerMetrics.layer1 < 1000,
    'Layer 2 <500ms': () => layerMetrics.layer2 < 500,
    'Layer 3 <2s': () => layerMetrics.layer3 < 2000,
    'response has metrics': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.metrics !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  errorRate.add(!success);

  // Log slow retrievals
  if (totalDuration > 5000) {
    console.warn(
      `Slow retrieval: ${totalDuration}ms (L1:${layerMetrics.layer1}ms, L2:${layerMetrics.layer2}ms, L3:${layerMetrics.layer3}ms) for "${query.substring(0, 50)}..."`
    );
  }

  // Realistic think time between retrievals
  sleep(2);
}

// Summary handler with comprehensive reporting
export function handleSummary(data) {
  const e2eP50 = data.metrics.e2e_total_latency.values['p(50)'];
  const e2eP95 = data.metrics.e2e_total_latency.values['p(95)'];
  const e2eP99 = data.metrics.e2e_total_latency.values['p(99)'];

  const l1P95 = data.metrics.e2e_layer1_latency.values['p(95)'];
  const l2P95 = data.metrics.e2e_layer2_latency.values['p(95)'];
  const l3P95 = data.metrics.e2e_layer3_latency.values['p(95)'];

  const errors = data.metrics.e2e_errors.values.rate;
  const totalRetrievals = data.metrics.e2e_retrievals.values.count;
  const avgTokens = data.metrics.e2e_context_tokens.values.avg;

  const nfr1Pass = e2eP50 < 3000 && e2eP95 < 5000 && e2eP99 < 10000;
  const nfr2Pass = l1P95 < 1000;
  const nfr3Pass = l2P95 < 500;
  const nfr4Pass = l3P95 < 2000;

  const allPass = nfr1Pass && nfr2Pass && nfr3Pass && nfr4Pass && errors < 0.01;

  return {
    'stdout': `
╔════════════════════════════════════════════════════════════════╗
║        End-to-End Retrieval Performance Report                ║
╚════════════════════════════════════════════════════════════════╝

📊 End-to-End Performance:
   • Total Retrievals: ${totalRetrievals}
   • P50 Latency:      ${e2eP50.toFixed(2)}ms ${e2eP50 < 3000 ? '✅' : '❌'} (Target: <3s)
   • P95 Latency:      ${e2eP95.toFixed(2)}ms ${e2eP95 < 5000 ? '✅' : '❌'} (Target: <5s)
   • P99 Latency:      ${e2eP99.toFixed(2)}ms ${e2eP99 < 10000 ? '✅' : '❌'} (Target: <10s)
   • Error Rate:       ${(errors * 100).toFixed(2)}% ${errors < 0.01 ? '✅' : '❌'} (Target: <1%)

⚙️  Layer Breakdown (P95):
   • Layer 1 (Vector):  ${l1P95.toFixed(2)}ms ${nfr2Pass ? '✅' : '❌'} (Target: <1s)
   • Layer 2 (SQL):     ${l2P95.toFixed(2)}ms ${nfr3Pass ? '✅' : '❌'} (Target: <500ms)
   • Layer 3 (LLM):     ${l3P95.toFixed(2)}ms ${nfr4Pass ? '✅' : '❌'} (Target: <2s)

📝 Context Quality:
   • Avg Tokens:        ${avgTokens.toFixed(0)} tokens

🎯 NFR Validation Summary:
   ${nfr1Pass ? '✅ PASS' : '❌ FAIL'} - NFR1: End-to-end retrieval SLOs
   ${nfr2Pass ? '✅ PASS' : '❌ FAIL'} - NFR2: Layer 1 vector search
   ${nfr3Pass ? '✅ PASS' : '❌ FAIL'} - NFR3: Layer 2 SQL joins
   ${nfr4Pass ? '✅ PASS' : '❌ FAIL'} - NFR4: Layer 3 reformulation

${allPass ? '✅ ALL PERFORMANCE NFRS PASS' : '❌ PERFORMANCE ISSUES DETECTED'}

📈 Performance Recommendations:
   ${!nfr1Pass ? '⚠️  End-to-end latency exceeds targets - Investigate bottleneck layers' : ''}
   ${!nfr2Pass ? '⚠️  Layer 1 slow - Review HNSW index configuration' : ''}
   ${!nfr3Pass ? '⚠️  Layer 2 slow - Verify database indexes and query plan' : ''}
   ${!nfr4Pass ? '⚠️  Layer 3 slow - Check Haiku 4.5 latency and context size' : ''}
   ${e2eP95 > 4000 ? '⚠️  P95 approaching threshold - Monitor production closely' : ''}
   ${allPass ? '✅ Performance excellent - Ready for production load' : ''}

💡 Next Steps:
   1. Document these baseline results in docs/performance-baselines.md
   2. Run under sustained load: k6 run --duration 30m end-to-end-retrieval.k6.js
   3. Test with production-like embedding counts (10K+)
   4. Monitor memory usage and connection pooling under load
   5. Set up continuous performance monitoring in CI/CD

📊 Baseline Documentation Template:
   ## Performance Baseline - ${new Date().toISOString().slice(0,10)}
   - Environment: ${__ENV.ALEXANDRIA_ENV || 'local'}
   - Embeddings: ${__ENV.ALEXANDRIA_EMBEDDING_COUNT || 'TBD'}
   - P50: ${e2eP50.toFixed(2)}ms
   - P95: ${e2eP95.toFixed(2)}ms
   - P99: ${e2eP99.toFixed(2)}ms
   - Status: ${allPass ? 'PASS' : 'FAIL'}

    `,
    'summary.json': JSON.stringify(data, null, 2),
  };
}
