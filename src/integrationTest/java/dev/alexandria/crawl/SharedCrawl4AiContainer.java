package dev.alexandria.crawl;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton Crawl4AI container shared across integration tests.
 * Avoids starting multiple heavy containers (~4 GB RAM each).
 */
final class SharedCrawl4AiContainer {

    static final String IMAGE = "unclecode/crawl4ai:0.8.0";
    static final int PORT = 11235;

    static final GenericContainer<?> INSTANCE = new GenericContainer<>(
            DockerImageName.parse(IMAGE))
            .withExposedPorts(PORT)
            .withCreateContainerCmdModifier(cmd ->
                    cmd.getHostConfig().withShmSize(1024L * 1024L * 1024L))
            .waitingFor(Wait.forHttp("/health")
                    .forPort(PORT)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(120)));

    static {
        INSTANCE.start();
    }

    static String baseUrl() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(PORT);
    }

    private SharedCrawl4AiContainer() {}
}
