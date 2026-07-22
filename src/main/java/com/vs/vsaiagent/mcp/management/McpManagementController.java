package com.vs.vsaiagent.mcp.management;

import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.observability.service.ExecutionTraceRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mcp-management")
public class McpManagementController {

    private final ManagedMcpToolService service;
    private final ExecutionTraceRecorder traceRecorder;

    public McpManagementController(ManagedMcpToolService service,
                                   ExecutionTraceRecorder traceRecorder) {
        this.service = service;
        this.traceRecorder = traceRecorder;
    }

    @GetMapping("/health")
    public ApiResponse<McpRuntimeHealth> health() {
        return ApiResponse.success(service.health());
    }

    @GetMapping("/tools")
    public ApiResponse<List<McpToolDescriptor>> tools() {
        return ApiResponse.success(service.listTools());
    }

    @PostMapping("/tools/refresh")
    public ApiResponse<List<McpToolDescriptor>> refresh() {
        return ApiResponse.success(service.refreshCatalog());
    }

    @PostMapping("/tools/invoke")
    public ApiResponse<McpToolCallResult> invoke(@RequestBody InvokeRequest request) {
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("mcp.management.invoke", request, "mcp");
        McpToolCallResult result = service.invoke(request.toolName(), request.argumentsJson(), request.confirmed());
        if (result.success()) {
            traceRecorder.success(requestId, result, startedAt);
        } else {
            traceRecorder.fail(requestId, result.errorMessage(), startedAt);
        }
        return ApiResponse.success(result);
    }

    public record InvokeRequest(String toolName, String argumentsJson, boolean confirmed) {
    }
}
