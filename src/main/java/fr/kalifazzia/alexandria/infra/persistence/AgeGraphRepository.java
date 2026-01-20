package fr.kalifazzia.alexandria.infra.persistence;

import com.google.gson.Gson;
import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Apache AGE implementation of GraphRepository.
 *
 * <p>Executes Cypher queries via the cypher() function using standard JDBC.
 * Each query must specify an AS clause defining the return type as agtype.
 *
 * <p>The graph "alexandria" is created during schema initialization (01-init.sql).
 * HikariCP connection-init-sql loads the AGE extension and sets search_path on each connection.
 */
@Repository
public class AgeGraphRepository implements GraphRepository {

    private static final Logger log = LoggerFactory.getLogger(AgeGraphRepository.class);
    private static final String GRAPH_NAME = "alexandria";

    private final JdbcTemplate jdbcTemplate;
    private final Gson gson;

    public AgeGraphRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.gson = new Gson();
    }

    @Override
    public void createDocumentVertex(UUID documentId, String path) {
        String cypher = String.format("""
            CREATE (:Document {document_id: '%s', path: '%s'})
            """, documentId, escapeCypher(path));

        executeCypherUpdate(cypher);
        log.debug("Created Document vertex: {}", documentId);
    }

    @Override
    public void createChunkVertex(UUID chunkId, ChunkType type, UUID documentId) {
        String cypher = String.format("""
            CREATE (:Chunk {chunk_id: '%s', type: '%s', document_id: '%s'})
            """, chunkId, type.name().toLowerCase(), documentId);

        executeCypherUpdate(cypher);
        log.debug("Created Chunk vertex: {} ({})", chunkId, type);
    }

    @Override
    public void createParentChildEdge(UUID parentId, UUID childId) {
        String cypher = String.format("""
            MATCH (p:Chunk {chunk_id: '%s'}), (c:Chunk {chunk_id: '%s'})
            CREATE (p)-[:HAS_CHILD]->(c)
            """, parentId, childId);

        executeCypherUpdate(cypher);
        log.debug("Created HAS_CHILD edge: {} -> {}", parentId, childId);
    }

    @Override
    public void deleteChunksByDocumentId(UUID documentId) {
        String cypher = String.format("""
            MATCH (c:Chunk {document_id: '%s'})
            DETACH DELETE c
            """, documentId);

        executeCypherUpdate(cypher);
        log.debug("Deleted Chunk vertices for document: {}", documentId);
    }

    @Override
    public void deleteDocumentGraph(UUID documentId) {
        String cypher = String.format("""
            MATCH (d:Document {document_id: '%s'})
            DETACH DELETE d
            """, documentId);

        executeCypherUpdate(cypher);
        log.debug("Deleted Document vertex: {}", documentId);
    }

    @Override
    public void createReferenceEdge(UUID sourceDocId, UUID targetDocId, String linkText) {
        String cypher = String.format("""
            MATCH (s:Document {document_id: '%s'}), (t:Document {document_id: '%s'})
            CREATE (s)-[:REFERENCES {link_text: '%s'}]->(t)
            """, sourceDocId, targetDocId, escapeCypher(linkText != null ? linkText : ""));

        executeCypherUpdate(cypher);
        log.debug("Created REFERENCES edge: {} -> {} ('{}')", sourceDocId, targetDocId, linkText);
    }

    @Override
    public List<UUID> findRelatedDocuments(UUID documentId, int maxHops) {
        if (maxHops < 1 || maxHops > 10) {
            throw new IllegalArgumentException("maxHops must be between 1 and 10");
        }

        String cypher = String.format("""
            MATCH (d:Document {document_id: '%s'})-[:REFERENCES*1..%d]->(related:Document)
            WHERE related.document_id <> '%s'
            RETURN DISTINCT related.document_id AS doc_id
            """, documentId, maxHops, documentId);

        String sql = String.format(
            "SELECT * FROM cypher('%s', $$ %s $$) AS (doc_id agtype)",
            GRAPH_NAME, cypher
        );

        List<UUID> results = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            String agtypeValue = rs.getString("doc_id");
            String docIdStr = parseAgtypeString(agtypeValue);
            if (docIdStr != null) {
                try {
                    results.add(UUID.fromString(docIdStr));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID in graph data, skipping: '{}' (raw agtype: {})", docIdStr, agtypeValue);
                }
            }
        });

        log.debug("Found {} related documents for {} (maxHops={})", results.size(), documentId, maxHops);
        return results;
    }

    /**
     * Parses an agtype string value to extract the underlying string.
     * Agtype strings are JSON-encoded with quotes, e.g., "\"uuid-value\""
     *
     * @param agtypeValue The raw agtype value from ResultSet
     * @return The parsed string value, or null if parsing fails
     */
    private String parseAgtypeString(String agtypeValue) {
        if (agtypeValue == null || agtypeValue.isEmpty()) {
            return null;
        }
        try {
            // agtype strings come as JSON strings (quoted)
            return gson.fromJson(agtypeValue, String.class);
        } catch (Exception e) {
            log.warn("Failed to parse agtype value: {}", agtypeValue, e);
            return null;
        }
    }

    /**
     * Executes a Cypher update query (CREATE, DELETE, etc.).
     * The query is wrapped in the cypher() function with AS clause.
     *
     * @param cypher Cypher query to execute
     */
    private void executeCypherUpdate(String cypher) {
        String sql = String.format(
            "SELECT * FROM cypher('%s', $$ %s $$) AS (result agtype)",
            GRAPH_NAME, cypher
        );
        jdbcTemplate.execute(sql);
    }

    /**
     * Escapes special characters in strings for Cypher queries.
     * Prevents syntax errors and injection with special characters.
     *
     * @param value String value to escape
     * @return Escaped string safe for Cypher
     */
    private String escapeCypher(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("'", "''");
    }
}
