package com.vs.vsaiagent.observability.controller;

import com.vs.vsaiagent.observability.dto.FailQueryDTO;
import com.vs.vsaiagent.observability.entity.AgentRequestLogEntity;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.observability.vo.RequestTraceVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/observability")
public class ObservabilityController {

    private final ExecutionLogService executionLogService;

    public ObservabilityController(ExecutionLogService executionLogService) {
        this.executionLogService = executionLogService;
    }

    @GetMapping("/requests/{requestId}")
    public ApiResponse<RequestTraceVO> queryRequestTrace(@PathVariable String requestId) {
        return ApiResponse.success(executionLogService.queryTrace(requestId));
    }

    @GetMapping("/sessions/{sessionId}/requests")
    public ApiResponse<List<AgentRequestLogEntity>> queryBySession(@PathVariable String sessionId,
                                                                   @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(executionLogService.queryBySession(sessionId, limit));
    }

    @PostMapping("/requests/failures")
    public ApiResponse<List<AgentRequestLogEntity>> queryFailures(@RequestBody FailQueryDTO dto) {
        LocalDateTime start = LocalDateTime.parse(dto.getStartTime());
        LocalDateTime end = LocalDateTime.parse(dto.getEndTime());
        int limit = dto.getLimit() == null ? 100 : dto.getLimit();
        return ApiResponse.success(executionLogService.queryFailures(start, end, limit));
    }
}
