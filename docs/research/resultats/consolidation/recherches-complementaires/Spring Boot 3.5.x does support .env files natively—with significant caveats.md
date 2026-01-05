# Spring Boot 3.5.x does support .env files natively—with significant caveats

**Bottom line:** The syntax `spring.config.import=optional:file:.env[.properties]` works in Spring Boot 3.5.x without spring-dotenv, but it's an unofficial workaround with critical limitations. Spring Boot co-lead Phil Webb explicitly stated this works "by accident rather than design." For proper .env file support with relaxed binding, spring-dotenv remains the recommended solution.

## The native workaround works, but not how you'd expect

Spring Boot 3.5.x can load .env files using this syntax:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

The `[.properties]` is an **extension hint**—a mechanism Spring Boot provides for extensionless files (common in Kubernetes volume mounts). It tells Spring Boot to parse the file using `PropertiesPropertySourceLoader`, which expects `KEY=value` format.

**Critical limitation:** Your .env file must use canonical Spring property format, not environment variable format. This means `spring.datasource.url=jdbc:postgresql://...` works, but `SPRING_DATASOURCE_URL=jdbc:postgresql://...` does **not** get mapped to `spring.datasource.url`. Spring Boot's relaxed binding only applies to environment variables read from the actual OS environment—not properties loaded from files via config import.

## Natively supported formats vs .env files

Spring Boot 3.5.x officially supports two file formats through built-in `PropertySourceLoader` implementations:

- **PropertiesPropertySourceLoader**: `.properties`, `.xml`
- **YamlPropertySourceLoader**: `.yml`, `.yaml`

The `.env` format is explicitly **not** in this list. GitHub issue #24229 requesting native .env support has been open since November 2020 with a `pending-design-work` label and **50+** thumbs-up reactions, but the Spring team has stated they're focused on other priorities.

The Spring Boot team is considering three design approaches: automatically processing `.env` files in the working directory, a new `envfile:` prefix with dedicated `ConfigDataLoader`, or a proper `[.env]` extension hint. None have been implemented as of version **3.5.9**.

## Spring Boot 3.5's new env: prefix is different

Spring Boot 3.5.0 introduced the `env:` config import prefix, but it serves a different purpose—it loads configuration content embedded inside an environment variable, not from `.env` files:

```yaml
spring:
  config:
    import: env:HELM_APPLICATION_YAML[.yaml]
```

This feature targets Kubernetes/Helm deployments where YAML configuration is passed as a single environment variable. It does not provide .env file support.

## spring-dotenv provides what native Spring Boot lacks

The spring-dotenv library adds capabilities the native workaround cannot provide:

| Capability | Native Workaround | spring-dotenv |
|------------|-------------------|---------------|
| Auto-discovery of .env files | No—requires explicit path | Yes |
| Relaxed binding (`MY_VAR` → `my.var`) | No | Yes |
| UPPERCASE_UNDERSCORE key format | No | Yes |
| Zero-configuration setup | No | Yes |
| Export to System.getProperties() | No | Optional |

For Spring Boot 3.x projects, the dependency is:

```xml
<dependency>
  <groupId>me.paulschwarz</groupId>
  <artifactId>springboot3-dotenv</artifactId>
  <version>5.1.0</version>
  <optional>true</optional>
</dependency>
```

spring-dotenv does **not** use `spring.config.import`. It provides a custom `PropertySource` that loads early in Spring's lifecycle and automatically discovers `.env` files in the project root.

## Practical recommendation for Alexandria RAG server

For your Spring Boot 3.5.9 project with Java 25, the decision depends on your .env file format:

**If your .env uses Spring property format:**
```properties
# .env file
spring.ai.openai.api-key=sk-xxx
spring.datasource.url=jdbc:postgresql://localhost/alexandria
```

The native workaround suffices:
```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

**If your .env uses environment variable format:**
```properties
# .env file  
SPRING_AI_OPENAI_API_KEY=sk-xxx
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost/alexandria
```

You need spring-dotenv—or convert your keys to dot-notation format. The native approach will load these as literal properties named `SPRING_AI_OPENAI_API_KEY`, not `spring.ai.openai.api-key`.

## Official documentation URLs

- External configuration reference: https://docs.spring.io/spring-boot/reference/features/external-config.html
- PropertySourceLoader API: https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/env/PropertySourceLoader.html
- Spring Boot 3.5 release notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.5-Release-Notes
- GitHub issue #24229 (native .env support): https://github.com/spring-projects/spring-boot/issues/24229
- spring-dotenv repository: https://github.com/paulschwarz/spring-dotenv

## Conclusion

The syntax `spring.config.import=optional:file:.env[.properties]` works natively without spring-dotenv, but only for .env files using canonical Spring property names. For standard .env files with `UPPERCASE_UNDERSCORE` keys—the format shared with Docker Compose and most tooling—spring-dotenv remains necessary. If minimizing dependencies is the priority, use the native workaround but ensure your .env file uses `dot.notation.keys` format exclusively.