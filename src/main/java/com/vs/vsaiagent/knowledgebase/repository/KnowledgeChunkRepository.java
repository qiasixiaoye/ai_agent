package com.vs.vsaiagent.knowledgebase.repository;

import com.vs.vsaiagent.knowledgebase.entity.KnowledgeChunkEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class KnowledgeChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<KnowledgeChunkEntity> ROW_MAPPER = (rs, rowNum) -> KnowledgeChunkEntity.builder()
            .id(rs.getLong("id"))
            .chunkId(rs.getString("chunk_id"))
            .documentId(rs.getString("document_id"))
            .chunkIndex(rs.getInt("chunk_index"))
            .tokenCount(rs.getInt("token_count"))
            .content(rs.getString("content"))
            .metadataJson(rs.getString("metadata_json"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build();

    public void batchInsert(List<KnowledgeChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO kb_chunk(chunk_id,document_id,chunk_index,token_count,content,metadata_json,created_at) VALUES (?,?,?,?,?,?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                KnowledgeChunkEntity chunk = chunks.get(i);
                ps.setString(1, chunk.getChunkId());
                ps.setString(2, chunk.getDocumentId());
                ps.setInt(3, chunk.getChunkIndex());
                ps.setInt(4, chunk.getTokenCount());
                ps.setString(5, chunk.getContent());
                ps.setString(6, chunk.getMetadataJson());
                ps.setTimestamp(7, Timestamp.valueOf(chunk.getCreatedAt() == null ? LocalDateTime.now() : chunk.getCreatedAt()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    public void deleteByDocumentId(String documentId) {
        jdbcTemplate.update("DELETE FROM kb_chunk WHERE document_id = ?", documentId);
    }

    public int countByDocumentId(String documentId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_chunk WHERE document_id = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }

    public List<KnowledgeChunkEntity> listByDocumentId(String documentId) {
        String sql = "SELECT * FROM kb_chunk WHERE document_id = ? ORDER BY chunk_index ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, documentId);
    }
}
