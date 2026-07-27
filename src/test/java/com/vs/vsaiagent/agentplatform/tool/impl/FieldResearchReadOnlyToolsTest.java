package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldResearchReadOnlyToolsTest {

    @Test
    void weatherToolReturnsTraceableDemoEvidenceForBeijing() {
        ToolExecuteResult result = new FieldWeatherSummaryAgentTool().execute(request(Map.of("location", "北京朝阳区")));

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("演示数据"));
        assertTrue(result.getOutput().contains("北京朝阳区"));
        assertTrue(result.getOutput().contains("上午"));
        assertTrue(result.getOutput().contains("下午"));
    }

    @Test
    void transitToolDoesNotClaimLowRiskWhenDemoDataIsUnavailable() {
        ToolExecuteResult result = new PublicTransitRiskAgentTool().execute(request(Map.of("location", "北京朝阳区", "simulateFailure", true)));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("unavailable"));
        assertFalse(result.getErrorMessage().contains("低风险"));
    }

    private ToolExecuteRequest request(Map<String, Object> arguments) {
        return ToolExecuteRequest.builder().toolName("test").traceId("trace-demo").arguments(arguments).build();
    }
}
