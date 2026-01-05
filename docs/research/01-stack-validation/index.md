# 01 - Stack Validation

Research documents validating the Alexandria RAG Server technology stack (January 2026).

## Documents

| File | Description |
|------|-------------|
| [java-25-compatibility.md](java-25-compatibility.md) | Java 25 LTS GA status and readiness |
| [stack-validation-january-2026.md](stack-validation-january-2026.md) | Complete stack validation for January 2026 |
| [langchain4j-spring-boot-compatibility.md](langchain4j-spring-boot-compatibility.md) | Langchain4j and Spring Boot 3.5.x compatibility |
| [java-25-spring-boot-architecture.md](java-25-spring-boot-architecture.md) | Java 25 RAG server architecture with Spring Boot 3.5.9 |
| [comprehensive-stack-validation.md](comprehensive-stack-validation.md) | Exhaustive validation of the complete Alexandria stack |

## Key Findings

- Java 25 LTS (25.0.1) is GA with Virtual Threads (JEP 491)
- Spring Boot 3.5.9 is the last 3.x version, OSS support until June 2026
- Langchain4j 1.10.0 is GA but incompatible with Spring Boot 4.x
- Spring AI MCP SDK 1.1.2 GA provides HTTP Streamable transport
