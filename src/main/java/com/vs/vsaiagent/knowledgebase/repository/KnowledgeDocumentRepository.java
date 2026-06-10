package com.vs.vsaiagent.knowledgebase.repository;

import com.vs.vsaiagent.knowledgebase.entity.KnowledgeDocumentEntity;
import com.vs.vsaiagent.knowledgebase.enums.DocumentProcessStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<KnowledgeDocumentEntity> ROW_MAPPER = (rs, rowNum) -> KnowledgeDocumentEntity.builder()
            .id(rs.getLong("id"))
            .documentId(rs.getString("document_id"))
            .fileName(rs.getString("file_name"))
            .fileType(rs.getString("file_type"))
            .fileSize(rs.getLong("file_size"))
            .contentHash(rs.getString("content_hash"))
            .source(rs.getString("source"))
            .tags(rs.getString("tags"))
            .version(rs.getInt("version"))
            .status(DocumentProcessStatus.valueOf(rs.getString("status")))
            .errorMessage(rs.getString("error_message"))
            .uploadedAt(toLocalDateTime(rs.getTimestamp("uploaded_at")))
            .processedAt(toLocalDateTime(rs.getTimestamp("processed_at")))
            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
            .updatedAt(toLocalDateTime(rs.getTimestamp("updated_at")))
            .build();

    public void save(KnowledgeDocumentEntity entity) {
        String sql = "INSERT INTO kb_document(document_id,file_name,file_type,file_size,content_hash,source,tags,version,status,error_message,uploaded_at,processed_at,created_at,updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(sql,
                entity.getDocumentId(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getSource(),
                entity.getTags(),
                entity.getVersion(),
                entity.getStatus().name(),
                entity.getErrorMessage(),
                Timestamp.valueOf(entity.getUploadedAt() == null ? now : entity.getUploadedAt()),
                entity.getProcessedAt() == null ? null : Timestamp.valueOf(entity.getProcessedAt()),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now));
    }

    public Optional<KnowledgeDocumentEntity> findByDocumentId(String documentId) {
        String sql = "SELECT * FROM kb_document WHERE document_id = ? LIMIT 1";
        List<KnowledgeDocumentEntity> rows = jdbcTemplate.query(sql, ROW_MAPPER, documentId);
        return rows.stream().findFirst();
    }

    public Optional<KnowledgeDocumentEntity> findByContentHash(String contentHash) {
        String sql = "SELECT * FROM kb_document WHERE content_hash = ? ORDER BY created_at DESC LIMIT 1";
        List<KnowledgeDocumentEntity> rows = jdbcTemplate.query(sql, ROW_MAPPER, contentHash);
        return rows.stream().findFirst();
    }

    public int nextVersion(String source, String fileName) {
        String sql = "SELECT COALESCE(MAX(version),0)+1 FROM kb_document WHERE source = ? AND file_name = ?";
        Integer version = jdbcTemplate.queryForObject(sql, Integer.class, source, fileName);
        return version == null ? 1 : version;
    }

    public void updateStatus(String documentId, DocumentProcessStatus status, String errorMessage) {
        String sql = "UPDATE kb_document SET status=?, error_message=?, updated_at=? WHERE document_id=?";
        jdbcTemplate.update(sql, status.name(), errorMessage, Timestamp.valueOf(LocalDateTime.now()), documentId);
    }

    public void markSuccess(String documentId) {
        String sql = "UPDATE kb_document SET status=?, processed_at=?, error_message=NULL, updated_at=? WHERE document_id=?";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(sql, DocumentProcessStatus.SUCCESS.name(), now, now, documentId);
    }

    public List<KnowledgeDocumentEntity> listRecent(int limit) {
        String sql = "SELECT * FROM kb_document ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
