package com.vs.vsaiagent.knowledgebase.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS kb_document (
                    id BIGSERIAL PRIMARY KEY,
                    document_id VARCHAR(64) NOT NULL UNIQUE,
                    file_name VARCHAR(512) NOT NULL,
                    file_type VARCHAR(32) NOT NULL,
                    file_size BIGINT NOT NULL,
                    content_hash VARCHAR(128) NOT NULL,
                    source VARCHAR(128),
                    tags VARCHAR(512),
                    version INT NOT NULL DEFAULT 1,
                    status VARCHAR(32) NOT NULL,
                    error_message TEXT,
                    uploaded_at TIMESTAMP NOT NULL,
                    processed_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kb_document_hash ON kb_document(content_hash)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kb_document_status ON kb_document(status)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS kb_chunk (
                    id BIGSERIAL PRIMARY KEY,
                    chunk_id VARCHAR(64) NOT NULL UNIQUE,
                    document_id VARCHAR(64) NOT NULL,
                    chunk_index INT NOT NULL,
                    token_count INT NOT NULL,
                    content TEXT NOT NULL,
                    metadata_json JSONB,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kb_chunk_document_id ON kb_chunk(document_id)");
    }
}
