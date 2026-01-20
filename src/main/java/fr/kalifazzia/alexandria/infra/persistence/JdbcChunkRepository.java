package fr.kalifazzia.alexandria.infra.persistence;

import com.pgvector.PGvector;
import fr.kalifazzia.alexandria.core.model.Chunk;
import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.port.ChunkRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of ChunkRepository.
 * Stores chunks with PostgreSQL pgvector type for embedding vectors.
 */
@Repository
public class JdbcChunkRepository implements ChunkRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Chunk> chunkRowMapper;

    public JdbcChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkRowMapper = this::mapRow;
    }

    @Override
    public UUID saveChunk(UUID documentId, UUID parentChunkId, ChunkType type,
                          String content, float[] embedding, int position) {
        UUID id = UUID.randomUUID();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO chunks (id, document_id, parent_chunk_id, chunk_type,
                                       content, embedding, position)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setObject(1, id);
            ps.setObject(2, documentId);
            ps.setObject(3, parentChunkId);
            ps.setString(4, type.name().toLowerCase());
            ps.setString(5, content);
            ps.setObject(6, new PGvector(embedding));
            ps.setInt(7, position);
            return ps;
        });

        return id;
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        jdbcTemplate.update("DELETE FROM chunks WHERE document_id = ?", documentId);
    }

    @Override
    public List<Chunk> findByDocumentId(UUID documentId) {
        return jdbcTemplate.query("""
                SELECT id, document_id, parent_chunk_id, chunk_type, content, position, created_at
                FROM chunks
                WHERE document_id = ?
                ORDER BY position, chunk_type
                """, chunkRowMapper, documentId);
    }

    @Override
    public long count() {
        Long result = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chunks",
            Long.class
        );
        return result != null ? result : 0L;
    }

    @Override
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM chunks");
    }

    private Chunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID documentId = rs.getObject("document_id", UUID.class);
        UUID parentChunkId = rs.getObject("parent_chunk_id", UUID.class);
        String typeStr = rs.getString("chunk_type");
        if (typeStr == null) {
            throw new IllegalStateException("chunk_type is null for chunk ID: " + id);
        }
        ChunkType type = ChunkType.valueOf(typeStr.toUpperCase());
        String content = rs.getString("content");
        int position = rs.getInt("position");
        Timestamp timestamp = rs.getTimestamp("created_at");
        Instant createdAt = timestamp != null ? timestamp.toInstant() : null;

        return new Chunk(id, documentId, parentChunkId, type, content, position, createdAt);
    }
}
