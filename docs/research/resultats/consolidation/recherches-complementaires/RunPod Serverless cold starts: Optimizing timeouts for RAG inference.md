# RunPod Serverless cold starts: Optimizing timeouts for RAG inference

A **300-second read timeout is reasonable** for RunPod serverless with cold starts, but you should implement exponential backoff retries and consider using RunPod's async API pattern for maximum reliability. For your BGE-M3 embeddings and bge-reranker-v2-m3 workloads on L4 GPUs, expect **10-30 second cold starts** with proper configuration, reducible to **2-5 seconds** with FlashBoot when traffic is consistent.

Your intermittent usage pattern (developer using Claude Code) means cold starts will be frequent. The optimal strategy combines: (1) enabling FlashBoot (free, no downside), (2) baking models into your Docker image, (3) implementing robust retry logic in Resilience4j, and (4) using async job submission with polling rather than synchronous calls for maximum reliability.

## Cold start times for embedding and reranking models

For your specific workloads—**BGE-M3** (~1.06 GB) and **bge-reranker-v2-m3** (~1.5 GB)—running on Infinity server with L4 GPUs, cold start times vary significantly based on configuration:

| Configuration | Expected Cold Start | Notes |
|--------------|---------------------|-------|
| Model baked in Docker + FlashBoot working | **2-8 seconds** | Best case for intermittent traffic |
| Model baked in Docker, no FlashBoot | **10-20 seconds** | Typical for low-traffic endpoints |
| Model downloaded at startup | **30-60 seconds** | Includes network transfer time |
| Network volume storage | **60-120 seconds** | Users report worse performance than expected |

The official **worker-infinity-embedding** from RunPod supports both embedding and reranking models simultaneously. Loading both models together adds approximately 5-10 seconds to cold start compared to a single model. Your L4's **24 GB VRAM** easily accommodates both models (~3-4 GB total).

RunPod's FlashBoot feature works **probabilistically**—it retains worker state after spin-down and can revive containers in under 1 second. However, its effectiveness depends on endpoint popularity and traffic consistency. For a single-user scenario, FlashBoot benefits are reduced since "images that get used more get cached more."

## The 300-second timeout question

**Yes, 300 seconds is reasonable and now officially supported.** RunPod increased their HTTP timeout from 90 seconds to 300 seconds in early 2024 specifically to address cold start scenarios. You can use the `?wait=300000` parameter with the `/runsync` endpoint to wait up to 5 minutes for a response.

Key timeout values from RunPod documentation:

| Setting | Default | Maximum | Your Recommendation |
|---------|---------|---------|---------------------|
| Execution Timeout | 600s (10 min) | 24 hours | Keep default 600s |
| Idle Timeout | 5 seconds | Configurable | 5-15 seconds |
| Worker Init Timeout | 7 minutes | Configurable via `RUNPOD_INIT_TIMEOUT` | Increase to 600s for safety |
| HTTP Timeout (runsync) | — | 300 seconds | Use async for longer |

For jobs that might exceed 5 minutes due to cold starts plus inference time, use the **async pattern**: submit via `/run`, receive job ID immediately, then poll `/status` with exponential backoff. This eliminates HTTP timeout concerns entirely.

## Resilience4j configuration for Spring Boot

For your Java application, implement this retry configuration optimized for RunPod cold start scenarios:

```yaml
resilience4j:
  retry:
    instances:
      runpodInference:
        max-attempts: 6
        wait-duration: 2s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        exponential-max-wait-duration: 60s
        retry-exceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
          - org.springframework.web.client.HttpServerErrorException$ServiceUnavailable
```

Configure your HTTP client with **separate connection and read timeouts**:
- **Connection timeout**: 10 seconds (establishing TCP connection)
- **Read timeout**: 120-180 seconds (waiting for response)

The read timeout should be shorter than 300 seconds because your retry logic handles cold starts—you want fast failure and retry rather than waiting the full duration on a potentially stuck request.

## FlashBoot effectiveness and warm-up strategies

FlashBoot is **free with no additional cost** and should always be enabled. RunPod reports that 95% of cold starts are under 2.3 seconds when FlashBoot is working optimally. However, community reports reveal significant variability:

- **High-traffic endpoints**: Cold starts consistently under 2 seconds
- **Low-traffic endpoints**: FlashBoot provides inconsistent benefit
- **After extended inactivity**: Users report cold starts returning to 15-45 seconds despite FlashBoot

For your intermittent usage pattern, consider a **periodic warm-up strategy**. A scheduled task sending lightweight requests every 4-5 minutes keeps the endpoint responsive. The trade-off is minimal cost (a few cents per day) versus eliminating surprise cold starts during development sessions.

```java
@Scheduled(fixedRate = 240_000)  // Every 4 minutes
public void keepEndpointWarm() {
    webClient.get()
        .uri("/{endpointId}/health", endpointId)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30))
        .subscribe(result -> log.debug("Endpoint warm"),
                  error -> log.warn("Warm-up failed: {}", error.getMessage()));
}
```

Alternatively, set **Active Workers to 1** for guaranteed zero cold starts. Active workers receive a 20-30% billing discount and remain warm continuously. This costs more than scale-to-zero but provides predictable latency.

## Idle timeout and cost optimization

For your single-user, intermittent usage pattern, optimize for cost while accepting occasional cold starts:

- **Active Workers: 0** — Enables full scale-to-zero
- **Max Workers: 2** — Allows burst capacity if needed  
- **Idle Timeout: 5-15 seconds** — Default 5s is appropriate
- **FlashBoot: Enabled** — Always enable
- **Scaling Type: Queue Delay** — Better for sporadic traffic

The billing calculation is: **Cold start time + Execution time + Idle time**. Cold start time is billed as part of execution time. A user complaint highlighted this: "My execution time averaged 20 seconds, and since this bill is 300% higher, that means the cold start time would have been 1 minute per request."

For models baked into Docker images (strongly recommended), expect billable cold start overhead of **10-20 seconds** per request after scale-down. With models downloaded at startup, this extends to **30-60+ seconds**.

## Critical implementation recommendations

**Avoid network volumes for model storage.** Multiple users report that network volumes make cold starts *worse*, not better: "We've tried attaching a data storage to it, thinking that would lower cold start times... that made things even worse, the delay times going up to 2 minutes."

**Bake models into your Docker image.** This is the single most impactful optimization. Models load from local NVMe storage in seconds rather than downloading over network. For your ~2.5 GB total model weight, the image size increase is acceptable.

**Use the async API pattern for cold-start tolerance.** Instead of synchronous `/runsync`:

1. Submit job to `/run` → receive job ID immediately
2. Poll `/status/{jobId}` with exponential backoff (2s, 4s, 8s, 16s...)
3. Handle `IN_QUEUE` status gracefully (indicates cold start in progress)
4. Retrieve result when status becomes `COMPLETED`

**Monitor delay_time in responses.** RunPod includes `delayTime` (milliseconds in queue) in responses. Values over 5,000ms indicate a cold start occurred—use this for observability and debugging.

## Conclusion

For your Alexandria RAG server calling RunPod for BGE-M3 embeddings and reranking, the practical configuration is:

- **Read timeout**: 120-180 seconds with 5-6 retry attempts using exponential backoff
- **Expected cold start**: 10-30 seconds (models baked in image, FlashBoot enabled)
- **Worst case cold start**: 60+ seconds (mitigated by retry logic)
- **Recommended API pattern**: Async `/run` + polling for maximum reliability
- **Cost optimization**: Scale-to-zero with FlashBoot, accept cold start overhead

The 300-second timeout is supported but unnecessary if you implement proper retry logic—you'll get faster recovery from transient failures with shorter timeouts plus retries than with one long timeout. For a developer workflow with intermittent usage, cold starts are unavoidable without Active Workers, but proper client-side resilience makes them transparent to your application.