package com.vs.vsaiagent.agentplatform.service.impl;

import com.vs.vsaiagent.agentplatform.model.TaskExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskOrchestratorServiceImplTest {

    @Test
    void runAstroPhotoFlow_executesFourStepsAndPropagatesStepOutputs() {
        ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);

        ToolExecuteResult milkyWayResult = ToolExecuteResult.builder()
                .toolName("milkyway_rise").success(true).output("milkyway-output").costMs(10L).build();
        ToolExecuteResult lightPollutionResult = ToolExecuteResult.builder()
                .toolName("light_pollution").success(true).output("light-pollution-output").costMs(10L).build();
        ToolExecuteResult cloudCoverResult = ToolExecuteResult.builder()
                .toolName("cloud_cover").success(true).output("cloud-cover-output").costMs(10L).build();
        ToolExecuteResult summaryResult = ToolExecuteResult.builder()
                .toolName("astro_plan_summary").success(true).output("summary-output").costMs(10L).build();

        when(toolExecutionService.executeByName(argThat(req -> req != null && "milkyway_rise".equals(req.getToolName()))))
                .thenReturn(milkyWayResult);
        when(toolExecutionService.executeByName(argThat(req -> req != null && "light_pollution".equals(req.getToolName()))))
                .thenReturn(lightPollutionResult);
        when(toolExecutionService.executeByName(argThat(req -> req != null && "cloud_cover".equals(req.getToolName()))))
                .thenReturn(cloudCoverResult);
        when(toolExecutionService.executeByName(argThat(req -> req != null && "astro_plan_summary".equals(req.getToolName()))))
                .thenReturn(summaryResult);

        TaskOrchestratorServiceImpl service = new TaskOrchestratorServiceImpl(toolExecutionService);

        TaskExecuteResult result = service.runAstroPhotoFlow(39.9042, 116.4074, "2026-06-14");

        assertTrue(result.isSuccess());
        assertEquals(4, result.getExecutedSteps());
        assertEquals(4, result.getResults().size());

        ArgumentCaptor<ToolExecuteRequest> captor = ArgumentCaptor.forClass(ToolExecuteRequest.class);
        org.mockito.Mockito.verify(toolExecutionService, org.mockito.Mockito.times(4)).executeByName(captor.capture());

        ToolExecuteRequest summaryRequest = captor.getAllValues().stream()
                .filter(req -> "astro_plan_summary".equals(req.getToolName()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> summaryArgs = summaryRequest.getArguments();
        assertEquals("milkyway-output", summaryArgs.get("milkyWayResult"));
        assertEquals("light-pollution-output", summaryArgs.get("lightPollutionResult"));
        assertEquals("cloud-cover-output", summaryArgs.get("cloudCoverResult"));
    }
}
