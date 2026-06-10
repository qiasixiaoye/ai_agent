package com.vs.vsaiagent.observability.repository;

import com.vs.vsaiagent.observability.entity.AgentRequestLogEntity;
import com.vs.vsaiagent.observability.enums.ExecutionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentRequestLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentRequestLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<AgentRequestLogEntity> ROW_MAPPER = (rs, rowNum) -> AgentRequestLogEntity.builder()
            .id(rs.getLong("id"))
            .requestId(rs.getString("request_id"))
            .traceId(rs.getString("trace_id"))
            .sessionId(rs.getString("session_id"))
            .scene(rs.getString("scene"))
            .userInput(rs.getString("user_input"))
            .modelName(rs.getString("model_name"))
            .finalOutput(rs.getString("final_output"))
            .status(rs.getString("status"))
            .totalCostMs(rs.getLong("total_cost_ms"))
            .errorMessage(rs.getString("error_message"))
            .startedAt(toLocalDateTime(rs.getTimestamp("started_at")))
            .finishedAt(toLocalDateTime(rs.getTimestamp("finished_at")))
            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
            .build();

    public void insertStart(AgentRequestLogEntity entity) {
        String sql = """
                INSERT INTO agent_request_log
                (request_id, trace_id, session_id, scene, user_input, model_name, status, started_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        jdbcTemplate.update(sql, entity.getRequestId(), entity.getTraceId(), entity.getSessionId(), entity.getScene(),
                entity.getUserInput(), entity.getModelName(), entity.getStatus(), Timestamp.valueOf(entity.getStartedAt()));
    }

    public void updateFinish(String requestId, ExecutionStatus status, String finalOutput, String errorMessage, long totalCostMs) {
        String sql = """
                UPDATE agent_request_log
                SET status = ?, final_output = ?, error_message = ?, total_cost_ms = ?, finished_at = ?, created_at = created_at
                WHERE request_id = ?
                """;
        jdbcTemplate.update(sql, status.name(), finalOutput, errorMessage, totalCostMs, Timestamp.valueOf(LocalDateTime.now()), requestId);
    }

    public Optional<AgentRequestLogEntity> findByRequestId(String requestId) {
        String sql = "SELECT * FROM agent_request_log WHERE request_id = ? LIMIT 1";
        List<AgentRequestLogEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, requestId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<AgentRequestLogEntity> listBySessionId(String sessionId, int limit) {
        String sql = "SELECT * FROM agent_request_log WHERE session_id = ? ORDER BY started_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, sessionId, limit);
    }

    public List<AgentRequestLogEntity> listFailed(LocalDateTime start, LocalDateTime end, int limit) {
        String sql = """
                SELECT * FROM agent_request_log
                WHERE status = 'FAILED' AND started_at BETWEEN ? AND ?
                ORDER BY started_at DESC LIMIT ?
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, Timestamp.valueOf(start), Timestamp.valueOf(end), limit);
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
