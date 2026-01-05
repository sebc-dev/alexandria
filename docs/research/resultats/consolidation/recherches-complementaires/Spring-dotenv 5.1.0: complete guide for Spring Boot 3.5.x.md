# Spring-dotenv 5.1.0: complete guide for Spring Boot 3.5.x

The latest **spring-dotenv version 5.1.0** released on January 2, 2026 is fully compatible with your Spring Boot 3.5.9 project. The library has been restructured into multiple modules—you should use the `springboot3-dotenv` artifact specifically designed for Spring Boot 3.x applications. Java 25 compatibility is expected since the library requires Java 17+ as its baseline, and no issues have been reported with newer JDK versions.

## Version 5.x introduced a multi-module architecture

The spring-dotenv project underwent significant restructuring in version 5.x, splitting into dedicated artifacts for different Spring versions. This change directly addresses Spring Boot 3's modified auto-configuration mechanism (documented in GitHub issue #31).

| Artifact | Purpose |
|----------|---------|
| `spring-dotenv` | Core library for Spring Framework only (no auto-integration) |
| **`springboot3-dotenv`** | Auto-integration module for Spring Boot 3.x |
| `springboot4-dotenv` | Auto-integration module for Spring Boot 4.x |
| `spring-dotenv-bom` | Bill of Materials for version alignment |

For your Spring Boot 3.5.9 project, the `springboot3-dotenv` artifact provides seamless auto-configuration without manual setup.

## Correct dependency coordinates for your project

**Maven (recommended BOM approach):**
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.paulschwarz</groupId>
            <artifactId>spring-dotenv-bom</artifactId>
            <version>5.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>me.paulschwarz</groupId>
        <artifactId>springboot3-dotenv</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**Gradle Kotlin DSL:**
```kotlin
dependencies {
    implementation(platform("me.paulschwarz:spring-dotenv-bom:5.1.0"))
    developmentOnly("me.paulschwarz:springboot3-dotenv")
}
```

**Gradle Groovy DSL:**
```groovy
dependencies {
    implementation platform('me.paulschwarz:spring-dotenv-bom:5.1.0')
    developmentOnly 'me.paulschwarz:springboot3-dotenv'
}
```

Using `developmentOnly` or `optional` scope is recommended since .env files are intended for local development, not production deployment.

## Java 25 compatibility assessment

The library declares **Java 17** as its minimum requirement, aligning with Spring Boot 3.x's baseline. Since Java 25 is backwards-compatible with Java 17+ bytecode and APIs, no compatibility issues are expected. The underlying dependency `io.github.cdimascio:dotenv-java:3.2.0` similarly requires Java 11+ with no reported issues on modern JDKs.

Key compatibility factors:
- No reflection-based tricks or internal API usage that could break on newer JDKs
- Standard property source integration following Spring's established patterns
- The GitHub issue tracker shows zero reports of Java 21+ or Java 25 problems

## Recent release history and breaking changes

| Version | Date | Notable Changes |
|---------|------|-----------------|
| **5.1.0** | Jan 2, 2026 | Added BOM support, finalized multi-module structure |
| 5.0.1 | Dec 22, 2025 | Multi-module restructure release |
| 4.0.0 | May 2024 | **Breaking:** Java 11 baseline, upgraded dotenv-java to 3.0.0 |
| 3.0.0 | Feb 2024 | Removed `env.` prefix requirement |
| 2.5.4 | May 2023 | Last 2.x release with Spring properties auto-completion metadata |

**Migration note for v5.x:** The configuration property `systemProperties` has been renamed to `springdotenv.exportToSystemProperties`. The old name still works but logs a deprecation warning.

## Known issues relevant to your use case

Three open GitHub issues warrant attention:

1. **Issue #33** - The .env file doesn't load when running from a packaged JAR. This is expected behavior—.env is for development workflows. In production, use actual environment variables or Spring profiles.

2. **Issue #35** - Multiple .env file support (e.g., `.env` and `.env.dev`) is not built-in. Workaround: use Spring profiles with `application-{profile}.properties` for environment-specific configuration.

3. **Issue #36** - Some users report ".env not working" in certain setups. Ensure your .env file is in the project root directory where the application runs from.

## Configuration options available

```properties
# Disable the integration entirely
springdotenv.enabled=false

# Export .env variables to System.getProperties()
springdotenv.exportToSystemProperties=true

# Don't fail if .env file is missing (default: true)
springdotenv.ignoreIfMissing=true
```

These properties support relaxed binding in Spring Boot applications, so `spring-dotenv.enabled`, `SPRINGDOTENV_ENABLED`, and `springdotenv.enabled` all work interchangeably.

## Conclusion

For your RAG server project with Spring Boot 3.5.9 and Java 25, use **`me.paulschwarz:springboot3-dotenv:5.1.0`** with the BOM for clean version management. The library is actively maintained with the most recent release just days old, and the explicit Spring Boot 3.x module ensures proper auto-configuration. No Java 25 compatibility issues exist, though remember that .env loading is development-only—production deployments should inject environment variables through your deployment platform.