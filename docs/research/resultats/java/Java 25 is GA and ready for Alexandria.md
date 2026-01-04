# Java 25 is GA and ready for Alexandria

**Java 25 reached General Availability on September 16, 2025**, making it fully available for the Alexandria RAG Server project in January 2026. As an LTS release with **8 years of support**, Java 25 offers the best combination of stability, performance, and virtual threads maturity for your Spring Boot deployment.

Virtual Threads are production-ready and have been since Java 21, but Java 24+ delivers a critical improvement: **JEP 491 eliminates the synchronized pinning limitation** that previously degraded performance when virtual threads encountered `synchronized` blocks. For handling concurrent MCP SSE connections on modest hardware, this makes Java 25 the optimal choice.

## Release schedule confirms 6-month cadence

Oracle and OpenJDK maintain a strict 6-month release cycle with LTS versions every 2 years:

| Version | Release Date | Type | Status |
|---------|-------------|------|--------|
| Java 22 | March 19, 2024 | Non-LTS | Superseded |
| Java 23 | September 17, 2024 | Non-LTS | Superseded |
| Java 24 | March 18, 2025 | Non-LTS | Supported |
| **Java 25** | **September 16, 2025** | **LTS** | **Current** |
| Java 26 | March 2026 | Non-LTS | Upcoming |
| Java 29 | September 2027 | LTS | Future |

Java 25 supersedes Java 21 as the latest LTS. Oracle provides free updates under NFTC until September 2028, with commercial support extending to at least September 2033. Eclipse Adoptium/Temurin builds are typically available within 3 weeks of upstream release.

## Virtual Threads matured significantly since Java 21

Virtual Threads became a final feature in **Java 21 via JEP 444**, but the technology has improved substantially:

**Java 21 (baseline)**: Virtual Threads work well for I/O-bound workloads but suffer from "pinning"—when a virtual thread enters a `synchronized` block, it pins to its carrier thread, blocking other virtual threads from using that carrier. This limits scalability when interacting with libraries using `synchronized`.

**Java 24+ (major breakthrough)**: JEP 491 eliminates synchronized pinning entirely. Virtual threads can now unmount from carrier threads even inside synchronized blocks, delivering up to **70x performance improvement** in scenarios with heavy synchronization. The diagnostic flag `-Djdk.tracePinnedThreads` was removed because it's no longer needed.

**Java 25 (current)**: Includes all Java 24 improvements plus finalizes Scoped Values (JEP 506), providing an efficient ThreadLocal alternative designed for virtual threads. Structured Concurrency remains in preview (5th iteration).

Remaining limitations are minimal: pinning still occurs during JNI/foreign function calls and class initialization, but these rarely affect typical Spring Boot applications.

## Spring Boot 3.4+ requires an upgrade path

Spring Boot 3.4.x **OSS support ended December 31, 2025**, making it technically unsupported for January 2026 deployments. The compatibility matrix reveals your options:

| Spring Boot | Min Java | Max Java | Support Status |
|-------------|----------|----------|----------------|
| 3.4.x | 17 | 24 | **Ended** Dec 2025 |
| 3.5.x | 17 | **25** | Active through June 2026 |
| 4.0.x | 17 | **25** | Current through Dec 2026 |

**Critical finding**: Spring Boot 3.4.x does not officially support Java 25—it maxes out at Java 24. For Java 25 compatibility, you need Spring Boot 3.5.5+ or 4.0.x.

Virtual Threads configuration is straightforward since Spring Boot 3.2:

```properties
spring.threads.virtual.enabled=true
```

This single property enables virtual threads for Tomcat/Jetty request handling, `@Async` methods, `@Scheduled` tasks, and message listeners (Kafka, RabbitMQ). Spring documentation explicitly recommends **Java 24 or later** for the best virtual threads experience.

## Recommended configuration for Alexandria

For your self-hosted deployment targeting **Intel i5-4570 with 24GB RAM** and requiring concurrent MCP SSE connections, the optimal stack is:

- **Java 25** (LTS, synchronized pinning fix, Scoped Values)
- **Spring Boot 3.5.x** (active support, full Java 25 compatibility)
- **Virtual Threads enabled** via `spring.threads.virtual.enabled=true`

This configuration maximizes Virtual Thread efficiency on limited hardware. The elimination of synchronized pinning in Java 24+ is particularly valuable because Langchain4j and PostgreSQL JDBC drivers may use synchronized blocks internally—with Java 21, these could throttle your concurrent connections.

If you prefer cutting-edge features, Spring Boot 4.0.x (released November 2025) embraces Java 25 fully and includes Spring Framework 7.0. However, 3.5.x offers better ecosystem maturity for Langchain4j integration.

## Impact on architecture decisions

**Virtual Threads are production-ready** for your use case. The concerns that existed in early Java 21 adoption—particularly synchronized pinning—are resolved. For SSE connections that spend most time waiting on I/O (database queries, LLM API calls), Virtual Threads dramatically outperform platform thread pools on constrained hardware.

Three architectural implications:

1. **Don't pool Virtual Threads**: Create new virtual threads per request/connection rather than using fixed thread pools. Spring Boot handles this automatically when virtual threads are enabled.

2. **Monitor ThreadLocal usage**: With potentially thousands of concurrent virtual threads for SSE connections, heavy ThreadLocal usage causes memory pressure. Prefer Scoped Values (now stable in Java 25) for passing context.

3. **Native code boundaries**: If Langchain4j or pgvector uses JNI calls, those will still pin. Benchmark under realistic load to ensure your hardware handles the actual pinning profile.

The combination of Java 25 LTS stability, resolved pinning issues, and active Spring Boot 3.5.x support makes January 2026 an excellent time to deploy Alexandria with Virtual Threads.