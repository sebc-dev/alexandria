/**
 * Layer 2 Performance Test: SQL Joins (Technology Linking)
 *
 * Tests NFR3: Layer 2 ≤500ms for SQL JOIN via pivot table
 *
 * Validates:
 * - Technology linking performance via convention_technologies pivot
 * - SQL JOIN efficiency with indexes
 * - Documentation retrieval via technology_id
 *
 * SLO Target: p95 latency <500ms for Layer 2
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const layer2Latency = new Trend('layer2_join_latency');
const errorRate = new Rate('layer2_errors');
const joinCount = new Counter('layer2_joins');
const docsRetrieved = new Counter('layer2_docs_retrieved');

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 15 },  // Warm-up
    { duration: '1m', target: 30 },   // Ramp up to 30 concurrent
    { duration: '2m', target: 30 },   // Sustain 30 concurrent
    { duration: '30s', target: 60 },  // Spike to 60 concurrent
    { duration: '1m', target: 60 },   // Sustain spike
    { duration: '30s', target: 0 },   // Ramp down
  ],
  thresholds: {
    // NFR3: Layer 2 ≤500ms (p95)
    'layer2_join_latency': ['p(95)<500'],
    // Additional thresholds
    'layer2_join_latency': ['p(50)<250', 'p(99)<1000'],
    'layer2_errors': ['rate<0.01'], // <1% error rate
    'http_req_duration': ['p(95)<500'],
  },
};

// Sample technology IDs (these would come from Layer 1 in real scenario)
const sampleTechnologies = [
  'typescript',
  'postgresql',
  'drizzle-orm',
  'bun',
  'hono',
  'zod',
  'pgvector',
  'openai-api',
  'mcp-protocol',
  'playwright',
];

export default function () {
  const baseUrl = __ENV.ALEXANDRIA_BASE_URL || 'http://localhost:3000';
  const projectId = __ENV.ALEXANDRIA_PROJECT_ID || 'test-project';

  // Simulate Layer 1 results: 3-5 conventions with associated technologies
  const conventionCount = Math.floor(Math.random() * 3) + 3; // 3-5 conventions
  const technologyIds = [];

  for (let i = 0; i < conventionCount; i++) {
    const tech = sampleTechnologies[Math.floor(Math.random() * sampleTechnologies.length)];
    technologyIds.push(tech);
  }

  // Prepare request payload (Layer 2: link documentation)
  const payload = JSON.stringify({
    technologyIds: [...new Set(technologyIds)], // Deduplicate
    projectId: projectId,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Execute Layer 2 SQL JOIN
  const startTime = Date.now();
  const response = http.post(`${baseUrl}/api/link/documentation`, payload, params);
  const duration = Date.now() - startTime;

  // Record metrics
  layer2Latency.add(duration);
  joinCount.add(1);

  // Validate response
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response time <500ms': (r) => r.timings.duration < 500,
    'response time <250ms (p50 target)': (r) => r.timings.duration < 250,
    'response has documentation': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body.documentation) && body.documentation.length >= 0;
      } catch (e) {
        return false;
      }
    },
    'response has technology links': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.documentation.every(doc => Array.isArray(doc.technologies));
      } catch (e) {
        return false;
      }
    },
  });

  // Track documentation retrieved
  try {
    const body = JSON.parse(response.body);
    if (body.documentation) {
      docsRetrieved.add(body.documentation.length);
    }
  } catch (e) {
    // Ignore parse errors
  }

  errorRate.add(!success);

  // Log slow queries
  if (duration > 500) {
    console.warn(`Slow Layer 2 JOIN detected: ${duration}ms for ${technologyIds.length} technologies`);
  }

  // Realistic think time
  sleep(1);
}

// Summary handler
export function handleSummary(data) {
  const p50 = data.metrics.layer2_join_latency.values['p(50)'];
  const p95 = data.metrics.layer2_join_latency.values['p(95)'];
  const p99 = data.metrics.layer2_join_latency.values['p(99)'];
  const errors = data.metrics.layer2_errors.values.rate;
  const totalJoins = data.metrics.layer2_joins.values.count;
  const totalDocs = data.metrics.layer2_docs_retrieved.values.count;

  const nfr3Pass = p95 < 500;

  return {
    'stdout': `
╔════════════════════════════════════════════════════════════════╗
║          Layer 2 SQL Joins Performance Report                 ║
╚════════════════════════════════════════════════════════════════╝

📊 Performance Metrics:
   • Total JOINs:    ${totalJoins}
   • Docs Retrieved: ${totalDocs} (avg: ${(totalDocs / totalJoins).toFixed(1)} per query)
   • P50 Latency:    ${p50.toFixed(2)}ms
   • P95 Latency:    ${p95.toFixed(2)}ms ${nfr3Pass ? '✅' : '❌'} (Target: <500ms)
   • P99 Latency:    ${p99.toFixed(2)}ms
   • Error Rate:     ${(errors * 100).toFixed(2)}% ${errors < 0.01 ? '✅' : '❌'} (Target: <1%)

🎯 NFR3 Validation:
   ${nfr3Pass ? '✅ PASS' : '❌ FAIL'} - SQL JOIN p95 latency ${nfr3Pass ? 'meets' : 'exceeds'} 500ms threshold

📝 Recommendations:
   ${p50 > 250 ? '⚠️  P50 latency >250ms - Verify indexes on technology_id and project_id' : ''}
   ${p95 > 400 ? '⚠️  P95 approaching threshold - Review JOIN query plan with EXPLAIN ANALYZE' : ''}
   ${errors > 0.005 ? '⚠️  Error rate >0.5% - Investigate failed JOINs' : ''}
   ${nfr3Pass && p95 < 300 ? '✅ Excellent performance - Indexes optimized' : ''}

💾 Database Optimization:
   • Verify index: idx_convention_technologies_technology_id
   • Verify index: idx_documentation_technology_id
   • Run VACUUM ANALYZE on convention_technologies and documentation tables
   • Consider materialized views for frequently joined technology sets

💡 Next Steps:
   1. Run EXPLAIN ANALYZE on Layer 2 query:
      SELECT d.* FROM documentation d
      JOIN documentation_technologies dt ON d.id = dt.documentation_id
      WHERE dt.technology_id IN (...) AND d.project_id = '...'
   2. Compare with varying technology counts (1, 5, 10, 20)
   3. Document baseline in docs/performance-baselines.md

    `,
    'summary.json': JSON.stringify(data, null, 2),
  };
}
