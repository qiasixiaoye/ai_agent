package com.vs.vsaiagent.observability.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ObservabilitySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ObservabilitySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_request_log (
                    id BIGSERIAL PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL UNIQUE,
                    trace_id VARCHAR(64) NOT NULL,
                    session_id VARCHAR(128) NOT NULL,
                    scene VARCHAR(64) NOT NULL,
                    user_input TEXT,
                    model_name VARCHAR(128),
                    final_output TEXT,
                    status VARCHAR(32) NOT NULL,
                    total_cost_ms BIGINT,
                    error_message TEXT,
                    started_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_request_trace_id ON agent_request_log(trace_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_request_session_id ON agent_request_log(session_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_request_started_at ON agent_request_log(started_at)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_request_status_started ON agent_request_log(status, started_at)");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_stage_log (
                    id BIGSERIAL PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL,
                    trace_id VARCHAR(64),
                    session_id VARCHAR(128),
                    stage_type VARCHAR(32) NOT NULL,
                    stage_name VARCHAR(128) NOT NULL,
                    tool_name VARCHAR(128),
                    input_payload TEXT,
                    output_payload TEXT,
                    cost_ms BIGINT,
                    success BOOLEAN,
                    error_message TEXT,
                    event_time TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_stage_request_id ON agent_stage_log(request_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_stage_event_time ON agent_stage_log(event_time)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_stage_type_event ON agent_stage_log(stage_type, event_time)");
    }
}
