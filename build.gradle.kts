plugins {
    java
    id("org.springframework.boot") version "3.5.2"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("info.solidsoft.pitest") version "1.15.0"
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
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
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
    pitestVersion.set("1.19.1")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(listOf("dev.alexandria.*"))
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
    reports {
        create("html") {
            required = true
            outputLocation = layout.buildDirectory.file("reports/spotbugs/spotbugs.html")
        }
        create("xml") {
            required = true
            outputLocation = layout.buildDirectory.file("reports/spotbugs/spotbugs.xml")
        }
    }
}

// ---------------------------------------------------------------------------
// SonarCloud - Quality Dashboard
// ---------------------------------------------------------------------------
sonar {
    properties {
        property("sonar.projectKey", "PLACEHOLDER_PROJECT_KEY")
        property("sonar.organization", "PLACEHOLDER_ORGANIZATION")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml"
        )
    }
}
