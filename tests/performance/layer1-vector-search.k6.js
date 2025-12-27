/**
 * Layer 1 Performance Test: Vector Search (HNSW Index)
 *
 * Tests NFR2: Vector search ≤1s (95%) with pgvector HNSW index
 *
 * Validates:
 * - HNSW index performance with varying embedding counts (100/1K/10K)
 * - Cosine similarity search latency
 * - Top-k retrieval efficiency
 *
 * SLO Target: p95 latency <1s for vector search
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const searchLatency = new Trend('layer1_search_latency');
const errorRate = new Rate('layer1_errors');
const searchCount = new Counter('layer1_searches');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Warm-up: 10 concurrent searches
    { duration: '1m', target: 25 },   // Ramp up to 25 concurrent
    { duration: '2m', target: 25 },   // Sustain 25 concurrent searches
    { duration: '30s', target: 50 },  // Spike to 50 concurrent
    { duration: '1m', target: 50 },   // Sustain spike
    { duration: '30s', target: 0 },   // Ramp down
  ],
  thresholds: {
    // NFR2: Vector search ≤1s (95%)
    'layer1_search_latency': ['p(95)<1000'],
    // Additional thresholds for performance analysis
    'layer1_search_latency': ['p(50)<500', 'p(99)<2000'],
    'layer1_errors': ['rate<0.01'], // <1% error rate
    'http_req_duration': ['p(95)<1000'],
  },
};

// Test data: Sample search queries
const sampleQueries = [
  'TypeScript best practices for hexagonal architecture',
  'PostgreSQL pgvector HNSW index optimization',
  'Drizzle ORM transaction management patterns',
  'Zod validation at application boundaries',
  'Bun runtime performance optimization',
  'OpenAI embeddings API rate limiting strategies',
  'MCP protocol stdio transport implementation',
  'Multi-project isolation filtering patterns',
  'Claude Code sub-agent orchestration',
  'Test-driven development with Playwright',
];

export default function () {
  const baseUrl = __ENV.ALEXANDRIA_BASE_URL || 'http://localhost:3000';
  const projectId = __ENV.ALEXANDRIA_PROJECT_ID || 'test-project';

  // Select random query for variety
  const query = sampleQueries[Math.floor(Math.random() * sampleQueries.length)];

  // Prepare request payload (simulating MCP tool call)
  const payload = JSON.stringify({
    query: query,
    projectId: projectId,
    topK: 5, // Retrieve top 5 most similar conventions
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Execute Layer 1 vector search
  const startTime = Date.now();
  const response = http.post(`${baseUrl}/api/search/conventions`, payload, params);
  const duration = Date.now() - startTime;

  // Record metrics
  searchLatency.add(duration);
  searchCount.add(1);

  // Validate response
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response time <1s': (r) => r.timings.duration < 1000,
    'response time <500ms (p50 target)': (r) => r.timings.duration < 500,
    'response has results': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.conventions) && body.conventions.length > 0;
      } catch (e) {
        return false;
      }
    },
    'response has similarity scores': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.conventions.every(c => typeof c.similarity === 'number');
      } catch (e) {
        return false;
      }
    },
  });

  errorRate.add(!success);

  // Log slow queries for analysis
  if (duration > 1000) {
    console.warn(`Slow query detected: ${duration}ms for "${query}"`);
  }

  // Realistic think time between searches
  sleep(1);
}

// Summary handler for detailed reporting
export function handleSummary(data) {
  const p50 = data.metrics.layer1_search_latency.values['p(50)'];
  const p95 = data.metrics.layer1_search_latency.values['p(95)'];
  const p99 = data.metrics.layer1_search_latency.values['p(99)'];
  const errors = data.metrics.layer1_errors.values.rate;
  const totalSearches = data.metrics.layer1_searches.values.count;

  const nfr2Pass = p95 < 1000;

  return {
    'stdout': `
╔════════════════════════════════════════════════════════════════╗
║          Layer 1 Vector Search Performance Report             ║
╚════════════════════════════════════════════════════════════════╝

📊 Performance Metrics:
   • Total Searches: ${totalSearches}
   • P50 Latency:    ${p50.toFixed(2)}ms
   • P95 Latency:    ${p95.toFixed(2)}ms ${nfr2Pass ? '✅' : '❌'} (Target: <1000ms)
   • P99 Latency:    ${p99.toFixed(2)}ms
   • Error Rate:     ${(errors * 100).toFixed(2)}% ${errors < 0.01 ? '✅' : '❌'} (Target: <1%)

🎯 NFR2 Validation:
   ${nfr2Pass ? '✅ PASS' : '❌ FAIL'} - Vector search p95 latency ${nfr2Pass ? 'meets' : 'exceeds'} 1s threshold

📝 Recommendations:
   ${p50 > 500 ? '⚠️  P50 latency >500ms - Consider HNSW index tuning (m, ef_construction)' : ''}
   ${p95 > 800 ? '⚠️  P95 approaching threshold - Monitor under production load' : ''}
   ${errors > 0.005 ? '⚠️  Error rate >0.5% - Investigate failed queries' : ''}
   ${nfr2Pass && p95 < 500 ? '✅ Excellent performance - Current HNSW config optimal' : ''}

💡 Next Steps:
   1. Run with 1K embeddings: ALEXANDRIA_EMBEDDING_COUNT=1000 k6 run layer1-vector-search.k6.js
   2. Run with 10K embeddings: ALEXANDRIA_EMBEDDING_COUNT=10000 k6 run layer1-vector-search.k6.js
   3. Compare baseline results across embedding counts
   4. Document baseline in docs/performance-baselines.md

    `,
    'summary.json': JSON.stringify(data, null, 2),
  };
}
