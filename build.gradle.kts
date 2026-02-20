import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    jacoco
    alias(libs.plugins.pitest)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
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
    // Spring Boot
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)

    // AI / Embeddings
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.embeddings.bge)
    implementation(libs.langchain4j.pgvector)
    implementation(libs.langchain4j.onnx.scoring)
    implementation(libs.spring.ai.mcp.server.webmvc)

    // Markdown Parsing
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)

    // Web Crawling
    implementation(libs.crawler.commons)

    // Database
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit)

    // Error Prone
    errorprone(libs.errorprone.core)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.ai.bom.get().toString())
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
                implementation(libs.spring.boot.starter.test)
                implementation(libs.spring.boot.starter.data.jpa)
                implementation(libs.spring.boot.testcontainers)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junit5)
                implementation(libs.langchain4j.core)
                implementation(libs.langchain4j.embeddings.bge)
                implementation(libs.langchain4j.pgvector)
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        // Docker 29+ requires API version >= 1.44; Testcontainers defaults to 1.32
                        systemProperty("api.version", "1.44")
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

// Disable plain jar — only produce the Spring Boot fat jar
tasks.named<Jar>("jar") { enabled = false }

// With jar disabled, implementation(project()) in test suites cannot resolve main classes.
// Explicitly wire main output into the integrationTest classpath.
sourceSets.named("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

// ---------------------------------------------------------------------------
// JaCoCo - Code Coverage
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = libs.versions.jacoco.get()
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
    pitestVersion.set(libs.versions.pitest.engine.get())
    junit5PluginVersion.set(libs.versions.pitest.junit5.get())
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
        property("sonar.projectKey", "sebc-dev_alexandria")
        property("sonar.organization", "sebc-dev")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
        )
    }
}

// ---------------------------------------------------------------------------
// Error Prone - Compile-time Bug Detection
// ---------------------------------------------------------------------------
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        isEnabled.set(true)
        disableWarningsInGeneratedCode.set(true)
        excludedPaths.set(".*/build/generated/.*")
    }
}

// ---------------------------------------------------------------------------
// Spotless - Code Formatting (google-java-format)
// ---------------------------------------------------------------------------
spotless {
    // ratchetFrom only checks files changed since origin/master (optimization).
    // JGit cannot resolve git worktree .git files, so skip ratchet in worktrees.
    val dotGit = rootProject.file(".git")
    if (dotGit.isDirectory) {
        ratchetFrom("origin/master")
    }
    java {
        target("src/**/*.java")
        googleJavaFormat()
    }
}
