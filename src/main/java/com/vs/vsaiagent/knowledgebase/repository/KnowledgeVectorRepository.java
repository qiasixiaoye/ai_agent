package com.vs.vsaiagent.knowledgebase.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int deleteByDocumentId(String documentId) {
        String sql = "DELETE FROM vector_store WHERE (metadata::jsonb ->> 'document_id') = ?";
        return jdbcTemplate.update(sql, documentId);
    }

    public void rebuildIndex() {
        jdbcTemplate.execute("REINDEX INDEX IF EXISTS vector_store_embedding_idx");
        jdbcTemplate.execute("ANALYZE vector_store");
    }
}
