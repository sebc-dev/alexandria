# Testcontainers 2.0.3 PostgreSQL artifact is `testcontainers-postgresql`

The Alexandria project's tech-spec using `testcontainers-postgresql` with version 2.0.3 is **correct**. However, there's a key clarification: the naming convention described in the question is actually reversed—**1.x used `postgresql` (no prefix), while 2.x uses `testcontainers-postgresql` (with prefix)**.

## Correct Maven coordinates for PostgreSQL 2.0.3

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
```

The groupId remains `org.testcontainers` in version 2.x. Version **2.0.3 was released December 15, 2025** and is the latest stable release. The artifact `org.testcontainers:postgresql:2.0.3` does **not exist**—that artifact ID stopped at version 1.21.4.

## BOM coordinates for version management

Testcontainers 2.x provides a Bill of Materials for centralized version management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>2.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

When using the BOM, individual module dependencies can omit the version tag. For Gradle 5.0+, use `implementation platform('org.testcontainers:testcontainers-bom:2.0.3')`.

## Module naming changes between 1.x and 2.x

The 2.0.0 release introduced a **universal `testcontainers-` prefix** for all modules. This table documents the most commonly used modules:

| 1.x Artifact ID | 2.x Artifact ID |
|-----------------|-----------------|
| `postgresql` | `testcontainers-postgresql` |
| `mysql` | `testcontainers-mysql` |
| `mariadb` | `testcontainers-mariadb` |
| `mongodb` | `testcontainers-mongodb` |
| `mssqlserver` | `testcontainers-mssqlserver` |
| `cassandra` | `testcontainers-cassandra` |
| `neo4j` | `testcontainers-neo4j` |
| `kafka` | `testcontainers-kafka` |
| `rabbitmq` | `testcontainers-rabbitmq` |
| `elasticsearch` | `testcontainers-elasticsearch` |
| `localstack` | `testcontainers-localstack` |
| `selenium` | `testcontainers-selenium` |
| `nginx` | `testcontainers-nginx` |
| `mockserver` | `testcontainers-mockserver` |
| `vault` | `testcontainers-vault` |
| `pulsar` | `testcontainers-pulsar` |
| `jdbc` | `testcontainers-jdbc` |
| `junit-jupiter` | `testcontainers-junit-jupiter` |

The core module `testcontainers` remains unchanged. The pattern is consistent: every database, messaging, and utility module now uses the `testcontainers-[name]` format.

## Package relocations require import changes

Beyond artifact renaming, **Java package paths changed** in 2.x. Container classes moved from `org.testcontainers.containers` to module-specific packages:

| 1.x Import | 2.x Import |
|------------|------------|
| `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers.postgresql.PostgreSQLContainer` |
| `org.testcontainers.containers.MySQLContainer` | `org.testcontainers.mysql.MySQLContainer` |
| `org.testcontainers.containers.MongoDBContainer` | `org.testcontainers.mongodb.MongoDBContainer` |
| `org.testcontainers.containers.KafkaContainer` | `org.testcontainers.kafka.KafkaContainer` |

The old class locations are deprecated but still exist for backward compatibility. Other breaking changes include `DockerComposeContainer` renamed to `ComposeContainer` and `getContainerIpAddress()` renamed to `getHost()`.

## Automated migration with OpenRewrite

For projects upgrading from 1.x to 2.x, OpenRewrite provides an automated migration recipe that handles dependency renaming, package relocations, and API changes:

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-testing-frameworks:RELEASE \
  -Drewrite.activeRecipes=org.openrewrite.java.testing.testcontainers.Testcontainers2Migration
```

## Conclusion

The Alexandria tech-spec's `testcontainers-postgresql:2.0.3` dependency declaration is **correct** for Testcontainers 2.x. The confusion likely stems from the counterintuitive naming history: 1.x used shorter names like `postgresql`, while 2.x adopted the more explicit `testcontainers-postgresql` format. When updating code, ensure imports also reflect the new package structure (`org.testcontainers.postgresql.PostgreSQLContainer`). Using the BOM (`testcontainers-bom:2.0.3`) is recommended for managing versions across multiple Testcontainers modules.