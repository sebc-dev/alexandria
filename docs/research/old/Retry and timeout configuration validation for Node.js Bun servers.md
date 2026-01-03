# Retry and timeout configuration validation for Node.js/Bun servers

Your configuration has **two correctly-set values** (max attempts, PostgreSQL timeout), **one appropriate value** (drain timeout), and **four parameters that need adjustment** (base delay, multiplier, jitter percentage, and model loading timeout). This analysis draws from AWS, Google Cloud, and Azure documentation alongside library-specific research to provide validated recommendations.

## Retry configuration: multiplier and jitter need adjustment

**Max attempts of 3 is industry standard.** AWS SDK Standard Mode, Azure SDK, and Google Cloud Storage all default to exactly 3 max retries. This provides adequate recovery from transient failures without overwhelming services during outages. For background/batch processes, you could extend to 5 attempts, but 3 is optimal for user-facing operations.

**Your 100ms base delay is too aggressive—increase to 500ms.** Google Cloud recommends **500ms** as the initial interval, while Azure defaults to **800ms**. Microsoft explicitly warns that "retrying after a delay shorter than 5 seconds risks overwhelming the cloud service" for database operations. Your 100ms starting point could contribute to cascade failures during partial outages. For database and embedding operations specifically, **500-1000ms** provides better backpressure.

**The 4x multiplier is non-standard—use 2x instead.** Every major cloud provider uses binary exponential backoff (2x multiplier). AWS, Google Cloud's direct APIs, and Azure all standardize on 2x. Google's HTTP client library uses 1.5x for even more gradual increase. A 4x multiplier escalates delays too rapidly, resulting in the sequence 100ms → 400ms → 1600ms rather than the more gradual 500ms → 1000ms → 2000ms that industry patterns produce.

**±20% jitter is too conservative—increase to ±50% or use full jitter.** Google Cloud explicitly documents a **randomization factor of 0.5 (±50%)**, meaning a 2-second calculated delay actually ranges from 1-3 seconds. AWS goes further, recommending "Full Jitter" where `sleep = random(0, calculated_backoff)`, which their research shows reduces total API calls by over half compared to smaller jitter values. Your ±20% provides insufficient load distribution during concurrent retry storms.

| Parameter | Your Value | Industry Standard | Action Required |
|-----------|------------|-------------------|-----------------|
| Max attempts | 3 | 3 | ✓ No change |
| Base delay | 100ms | 500-800ms | ⚠️ Increase to 500ms |
| Multiplier | 4x | 2x | ⚠️ Reduce to 2x |
| Jitter | ±20% | ±50% or 0-100% | ⚠️ Increase to ±50% |

## Jitter formula is mathematically correct but should use higher percentage

Your formula `jitter = 1 + (Math.random() - 0.5) * 2 * (jitterPercent / 100)` correctly produces a symmetric distribution. For 20% jitter, it generates multipliers from 0.8 to 1.2. For 50% jitter, it would produce 0.5 to 1.5—which matches Google Cloud's documented formula exactly.

Google's equivalent formula: `randomized_interval = retry_interval * (1 + randomization_factor * (2 * random() - 1))`. Your implementation is mathematically identical when `randomization_factor = jitterPercent / 100`.

**Recommended change**: Keep the formula, but pass **50** instead of **20** as the jitter percentage. Alternatively, consider AWS Full Jitter: `delay = Math.random() * calculatedBackoff`, which provides asymmetric jitter from 0 to the calculated value and performs better under high contention.

## Model loading timeout of 60s is insufficient for first download

The **Xenova/multilingual-e5-small** model is significantly larger than the ~100MB estimate in your configuration. The ONNX model files are:

- **model_int8.onnx (quantized)**: 118 MB
- **model.onnx (fp32 default)**: 470 MB
- **Total ONNX folder**: 2.06 GB (all variants)

Download time estimates for the 118MB quantized model vary dramatically by connection: **10 seconds** on fast connections (100 Mbps), **40 seconds** on moderate connections (25 Mbps), and **100+ seconds** on slower connections. Additional overhead includes ONNX Runtime WebAssembly initialization (2-5 seconds), model parsing (1-3 seconds), and tokenizer loading.

**Recommended timeouts:**
- First-time download (quantized ~118MB): **120-180 seconds**
- First-time download (fp32 ~470MB): **300 seconds**  
- Cached model loading: **60 seconds** (current value is fine for cached loads)

Transformers.js does not document explicit timeout recommendations, but GitHub issues report that larger models routinely require 30+ seconds even on fast connections. Bun's default fetch timeout is 5 minutes (300 seconds), matching Node.js, so your application timeout will be the limiting factor.

## PostgreSQL 5-second pool close timeout matches documentation exactly

The postgres.js README explicitly uses `await sql.end({ timeout: 5 })` in its official graceful shutdown example. This timeout is appropriate because postgres.js handles shutdown gracefully: calling `sql.end()` immediately rejects new queries while allowing in-flight queries to complete. The 5-second timeout acts as a maximum wait—if queries finish sooner, shutdown completes faster.

**Behavior during shutdown:**
1. New queries immediately rejected with `CONNECTION_ENDED` error
2. In-flight queries allowed to complete (up to timeout)
3. If timeout reached: pending queries rejected with `CONNECTION_DESTROYED`, connections forcibly closed

**Known caveat**: GitHub issue #1097 documents that `sql.end()` can hang indefinitely if the server disconnects mid-query. Always using a timeout (as you do) mitigates this. For Drizzle ORM, ensure you export and explicitly close the underlying `sql` connection—Drizzle does not manage pool lifecycle.

```javascript
// Correct Drizzle + postgres.js pattern
const sql = postgres(process.env.DATABASE_URL);
const db = drizzle(sql);
// On shutdown:
await sql.end({ timeout: 5 });
```

## Graceful shutdown drain timeout of 30s is appropriate

**30 seconds matches Kubernetes' default `terminationGracePeriodSeconds`** and is explicitly recommended by Google Cloud as the baseline. This duration provides adequate time for load balancers to stop routing new requests, in-flight HTTP requests to complete, database connections to close, and message queues to flush.

Framework defaults vary considerably—**http-terminator** defaults to 5 seconds, **close-with-grace** (used by Fastify) defaults to 10 seconds—but 30 seconds provides a safety margin for real-world conditions. For Bun specifically, `server.stop()` provides built-in graceful shutdown that waits for in-flight requests without a default timeout, so you must implement your own timeout wrapper.

**Signal handling**: Handle both SIGTERM (system/orchestrator shutdown) and SIGINT (Ctrl+C). Use a guard flag to prevent duplicate handling, and set a force-exit timeout to prevent zombie processes.

**Resource cleanup order should be:**
1. Stop accepting new requests (`server.close()`)
2. Wait for in-flight requests (up to drain timeout)
3. Close external connections: message queues → cache → database
4. Run application cleanup callbacks
5. Exit process

## MCP SDK shutdown relies on transport-level patterns

The MCP protocol specification defines a three-phase lifecycle (initialization, operation, shutdown) but delegates shutdown mechanism to transport. For **stdio transport**, the documented pattern is: close input stream → wait for server exit → SIGTERM if unresponsive → SIGKILL as fallback. For **HTTP transports**, shutdown is indicated by closing HTTP connections.

The TypeScript SDK's `StdioServerTransport` includes built-in graceful shutdown support. Close the transport explicitly in your shutdown handler: `transport.close()`. The SDK abstracts timeout and cancellation logic, but implementations should establish request timeouts per the specification.

## Recommended final configuration

```typescript
// Retry configuration (corrected)
const RETRY_CONFIG = {
  maxAttempts: 3,           // ✓ Keep as-is
  baseDelayMs: 500,         // ⚠️ Changed from 100ms
  multiplier: 2,            // ⚠️ Changed from 4x
  jitterPercent: 50,        // ⚠️ Changed from 20%
  maxDelayMs: 30_000,       // Add cap
};

// Jitter formula (unchanged, but with higher percentage)
const applyJitter = (delay: number, jitterPercent: number) =>
  delay * (1 + (Math.random() - 0.5) * 2 * (jitterPercent / 100));

// Timeout configuration (adjusted)
const TIMEOUTS = {
  modelLoadingFirstRun: 180_000,  // ⚠️ Changed from 60s to 180s
  modelLoadingCached: 60_000,     // Can keep 60s for cached
  gracefulShutdownDrain: 30_000,  // ✓ Keep as-is
  postgresPoolClose: 5,           // ✓ Keep as-is (seconds)
};
```

## Conclusion

The configuration reflects sound architectural thinking but benefits from aligning with cloud provider standards. The most critical change is increasing the model loading timeout—60 seconds will fail unpredictably on first downloads. Adjusting retry parameters to match AWS/Google patterns (2x multiplier, 500ms base, 50% jitter) provides better behavior during partial outages. Your PostgreSQL and drain timeouts are well-calibrated to library and industry defaults.