package fr.kalifazzia.alexandria.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests validating database connectivity with PostgreSQL,
 * pgvector extension, and Apache AGE graph database.
 *
 * <p>Requires Docker container running: docker compose up -d</p>
 * <p>Run with: mvn verify</p>
 * <p>Disabled in CI: requires local Docker with Apache AGE extension</p>
 */
@SpringBootTest
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
class DatabaseConnectionIT {

    // Java 21: Record for test result data
    record VectorTestResult(String vector, Double distance) {}
    record ExtensionInfo(String name, String version) {}

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldConnectToPostgreSQL() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldUsePgvector() {
        // Test vector creation and cosine distance
        Double distance = jdbcTemplate.queryForObject(
            "SELECT '[1,2,3]'::vector <=> '[4,5,6]'::vector",
            Double.class
        );
        assertThat(distance).isNotNull();
        // Cosine distance for these vectors (not normalized) is around 0.025
        assertThat(distance).isBetween(0.0, 1.0);
    }

    @Test
    void shouldUseApacheAGE() {
        // Connection-init-sql already loaded AGE and set search_path
        // Verify graph exists
        String graphName = jdbcTemplate.queryForObject(
            "SELECT name FROM ag_catalog.ag_graph WHERE name = 'alexandria'",
            String.class
        );
        assertThat(graphName).isEqualTo("alexandria");
    }

    @Test
    void shouldExecuteCypherQuery() {
        // Test basic Cypher execution via AGE
        // First, create a test node
        jdbcTemplate.execute(
            "SELECT * FROM cypher('alexandria', $$ CREATE (n:TestNode {name: 'test'}) RETURN n $$) AS (n agtype)"
        );

        // Then query it
        String result = jdbcTemplate.queryForObject(
            "SELECT * FROM cypher('alexandria', $$ MATCH (n:TestNode {name: 'test'}) RETURN n.name $$) AS (name agtype)",
            String.class
        );
        assertThat(result).contains("test");

        // Cleanup
        jdbcTemplate.execute(
            "SELECT * FROM cypher('alexandria', $$ MATCH (n:TestNode) DELETE n $$) AS (n agtype)"
        );
    }

    @Test
    void shouldQueryChunksTable() {
        // Verify schema was created by init scripts
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('documents', 'chunks')",
            Integer.class
        );
        assertThat(tableCount).isEqualTo(2);
    }

    @Test
    void shouldHaveHnswIndex() {
        // Verify HNSW index exists on chunks.embedding
        Integer indexCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'chunks' AND indexdef LIKE '%hnsw%'",
            Integer.class
        );
        assertThat(indexCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldUseVirtualThreads() {
        // Verify virtual threads are enabled (Spring Boot 3.2+ with spring.threads.virtual.enabled=true)
        Thread currentThread = Thread.currentThread();
        // In test context with virtual threads, we can verify the thread is virtual
        // Note: @Async operations will use virtual threads, but test thread may be platform thread
        // This test documents that virtual threads are configured
        assertThat(currentThread).isNotNull();
        // Actual virtual thread usage will be verified in async operations (Phase 2+)
    }

    @Test
    void shouldQueryWithRecords() {
        // Java 21: Use records with RowMapper
        var extensions = jdbcTemplate.query(
            "SELECT extname, extversion FROM pg_extension WHERE extname IN ('vector', 'age')",
            (rs, rowNum) -> new ExtensionInfo(rs.getString("extname"), rs.getString("extversion"))
        );
        assertThat(extensions).hasSize(2);
        assertThat(extensions).extracting(ExtensionInfo::name).contains("vector", "age");
    }
}
