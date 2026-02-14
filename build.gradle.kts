plugins {
    java
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("info.solidsoft.pitest") version "1.19.0-rc.3"
    id("com.github.spotbugs") version "6.4.8"
    id("org.sonarqube") version "7.2.2.6593"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("dev.langchain4j:langchain4j:1.11.0")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:1.11.0-beta19")
    implementation("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.11.0-beta19")
    implementation("dev.langchain4j:langchain4j-pgvector:1.11.0-beta19")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.3")
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("org.springframework.boot:spring-boot-testcontainers")
                implementation("org.testcontainers:postgresql")
                implementation("org.testcontainers:junit-jupiter")
                implementation("dev.langchain4j:langchain4j:1.11.0")
                implementation("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.11.0-beta19")
                implementation("dev.langchain4j:langchain4j-pgvector:1.11.0-beta19")
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

// ---------------------------------------------------------------------------
// JaCoCo - Code Coverage
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// ---------------------------------------------------------------------------
// PIT - Mutation Testing
// ---------------------------------------------------------------------------
pitest {
    pitestVersion.set("1.21.0")
    junit5PluginVersion.set("1.2.3")
    val pitTargetClasses = providers.gradleProperty("pitest.targetClasses")
    targetClasses.set(
        if (pitTargetClasses.isPresent) listOf(pitTargetClasses.get())
        else listOf("dev.alexandria.*")
    )
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    timeoutConstInMillis.set(10000)
    failWhenNoMutations.set(false)
}

// ---------------------------------------------------------------------------
// SpotBugs - Bug / Dead Code Detection
// ---------------------------------------------------------------------------
spotbugs {
    ignoreFailures = true
    effort = com.github.spotbugs.snom.Effort.DEFAULT
    reportLevel = com.github.spotbugs.snom.Confidence.DEFAULT
    excludeFilter = file("config/spotbugs/exclude-filter.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    val taskName = name
    reports {
        create("html") {
            required = true
            outputLocation = layout.buildDirectory.file("reports/spotbugs/${taskName}.html")
        }
        create("xml") {
            required = true
            outputLocation = layout.buildDirectory.file("reports/spotbugs/${taskName}.xml")
        }
    }
}

// ---------------------------------------------------------------------------
// SonarCloud - Quality Dashboard
// ---------------------------------------------------------------------------
sonar {
    properties {
        property("sonar.projectKey", providers.environmentVariable("SONAR_PROJECT_KEY").getOrElse(""))
        property("sonar.organization", providers.environmentVariable("SONAR_ORGANIZATION").getOrElse(""))
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
        )
    }
}
