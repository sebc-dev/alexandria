package fr.kalifazzia.alexandria.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of DocumentRepository.
 * Stores documents with PostgreSQL-specific types: TEXT[] for tags, JSONB for frontmatter.
 */
@Repository
public class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<Document> documentRowMapper;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.documentRowMapper = this::mapRow;
    }

    @Override
    public Document save(Document document) {
        UUID id = document.id() != null ? document.id() : UUID.randomUUID();
        Instant now = Instant.now();

        String sql = """
                INSERT INTO documents (id, path, title, category, tags, content_hash, frontmatter, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """;

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(sql);
            ps.setObject(1, id);
            ps.setString(2, document.path());
            ps.setString(3, document.title());
            ps.setString(4, document.category());
            ps.setArray(5, connection.createArrayOf("text", document.tags().toArray(new String[0])));
            ps.setString(6, document.contentHash());
            ps.setString(7, serializeFrontmatter(document.frontmatter()));
            ps.setTimestamp(8, Timestamp.from(now));
            ps.setTimestamp(9, Timestamp.from(now));
            return ps;
        });

        return new Document(
                id,
                document.path(),
                document.title(),
                document.category(),
                document.tags(),
                document.contentHash(),
                document.frontmatter(),
                now,
                now
        );
    }

    @Override
    public Optional<Document> findByPath(String path) {
        String sql = """
                SELECT id, path, title, category, tags, content_hash, frontmatter, created_at, updated_at
                FROM documents
                WHERE path = ?
                """;

        List<Document> results = jdbcTemplate.query(sql, documentRowMapper, path);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", id);
    }

    @Override
    public void deleteByPath(String path) {
        jdbcTemplate.update("DELETE FROM documents WHERE path = ?", path);
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String path = rs.getString("path");
        String title = rs.getString("title");
        String category = rs.getString("category");
        List<String> tags = arrayToList(rs.getArray("tags"));
        String contentHash = rs.getString("content_hash");
        Map<String, Object> frontmatter = deserializeFrontmatter(rs.getString("frontmatter"));
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();
        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : Instant.now();

        return new Document(id, path, title, category, tags, contentHash, frontmatter, createdAt, updatedAt);
    }

    private List<String> arrayToList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        String[] array = (String[]) sqlArray.getArray();
        return array != null ? List.of(array) : List.of();
    }

    private String serializeFrontmatter(Map<String, Object> frontmatter) {
        if (frontmatter == null || frontmatter.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(frontmatter);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize frontmatter to JSON", e);
        }
    }

    private Map<String, Object> deserializeFrontmatter(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize frontmatter from JSON", e);
        }
    }
}
