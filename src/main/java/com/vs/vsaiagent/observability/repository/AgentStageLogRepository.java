package com.vs.vsaiagent.observability.repository;

import com.vs.vsaiagent.observability.entity.AgentStageLogEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentStageLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentStageLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AgentStageLogEntity> ROW_MAPPER = (rs, rowNum) -> AgentStageLogEntity.builder()
            .id(rs.getLong("id"))
            .requestId(rs.getString("request_id"))
            .traceId(rs.getString("trace_id"))
            .sessionId(rs.getString("session_id"))
            .stageType(rs.getString("stage_type"))
            .stageName(rs.getString("stage_name"))
            .toolName(rs.getString("tool_name"))
            .inputPayload(rs.getString("input_payload"))
            .outputPayload(rs.getString("output_payload"))
            .costMs(rs.getLong("cost_ms"))
            .success(rs.getBoolean("success"))
            .errorMessage(rs.getString("error_message"))
            .eventTime(toLocalDateTime(rs.getTimestamp("event_time")))
            .build();

    public void insert(AgentStageLogEntity entity) {
        String sql = """
                INSERT INTO agent_stage_log
                (request_id, trace_id, session_id, stage_type, stage_name, tool_name, input_payload, output_payload, cost_ms, success, error_message, event_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql,
                entity.getRequestId(),
                entity.getTraceId(),
                entity.getSessionId(),
                entity.getStageType(),
                entity.getStageName(),
                entity.getToolName(),
                entity.getInputPayload(),
                entity.getOutputPayload(),
                entity.getCostMs(),
                entity.getSuccess(),
                entity.getErrorMessage(),
                Timestamp.valueOf(entity.getEventTime()));
    }

    public List<AgentStageLogEntity> listByRequestId(String requestId) {
        String sql = "SELECT * FROM agent_stage_log WHERE request_id = ? ORDER BY event_time ASC,id ASC";
        return jdbcTemplate.query(sql, ROW_MAPPER, requestId);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
