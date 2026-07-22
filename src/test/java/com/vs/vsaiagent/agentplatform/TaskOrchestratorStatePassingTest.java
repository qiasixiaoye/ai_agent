package com.vs.vsaiagent.agentplatform;

import com.vs.vsaiagent.agentplatform.model.TaskExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.TaskExecuteResult;
import com.vs.vsaiagent.agentplatform.model.TaskStepDefinition;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.agentplatform.service.impl.TaskOrchestratorServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务编排：多步骤调用 + 状态传递（${step:sX}）+ 结果汇总。
 * 用假的 ToolExecutionService 代替真实工具，离线验证编排逻辑（简历#3/#5）。
 */
class TaskOrchestratorStatePassingTest {

    /** 记录每次调用收到的（已解析）参数，并回显可识别的输出。 */
    static class RecordingToolExecutionService implements ToolExecutionService {
        final List<ToolExecuteRequest> calls = new ArrayList<>();

        @Override
        public ToolExecuteResult executeByName(ToolExecuteRequest request) {
            calls.add(request);
            return ToolExecuteResult.builder()
                    .toolName(request.getToolName())
                    .success(true)
                    .output("OUT:" + request.getToolName())
                    .costMs(1L)
                    .build();
        }

        @Override
        public ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request) {
            return executeByName(request);
        }

        @Override
        public List<ToolMetadata> listTools() {
            return List.of();
        }
    }

    @Test
    void passesPriorStepOutputIntoLaterStepArgsAndSummarizesLast() {
        RecordingToolExecutionService fake = new RecordingToolExecutionService();
        TaskOrchestratorServiceImpl orchestrator = new TaskOrchestratorServiceImpl(fake);

        TaskExecuteRequest request = new TaskExecuteRequest();
        request.setMaxSteps(6);
        request.setSteps(List.of(
                TaskStepDefinition.builder().stepId("s1").toolName("alpha")
                        .args(Map.of("q", "hello")).required(true).build(),
                TaskStepDefinition.builder().stepId("s2").toolName("beta")
                        .args(Map.of("prev", "${step:s1}")).required(true).build()
        ));

        TaskExecuteResult result = orchestrator.execute(request);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getExecutedSteps());
        // 结果汇总取最后一步输出
        assertEquals("OUT:beta", result.getSummary());
        // 状态传递：s2 的 prev 参数被解析为 s1 的真实输出，而非占位符原文
        ToolExecuteRequest secondCall = fake.calls.get(1);
        assertEquals("OUT:alpha", secondCall.getArguments().get("prev"));
    }

    @Test
    void stopsOnRequiredStepFailure() {
        ToolExecutionService failing = new ToolExecutionService() {
            @Override
            public ToolExecuteResult executeByName(ToolExecuteRequest request) {
                return ToolExecuteResult.builder().toolName(request.getToolName())
                        .success(false).errorMessage("boom").build();
            }

            @Override
            public ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request) {
                return executeByName(request);
            }

            @Override
            public List<ToolMetadata> listTools() {
                return List.of();
            }
        };
        TaskOrchestratorServiceImpl orchestrator = new TaskOrchestratorServiceImpl(failing);

        TaskExecuteRequest request = new TaskExecuteRequest();
        request.setSteps(List.of(
                TaskStepDefinition.builder().stepId("s1").toolName("alpha")
                        .args(Map.of()).required(true).build(),
                TaskStepDefinition.builder().stepId("s2").toolName("beta")
                        .args(Map.of()).required(true).build()
        ));

        TaskExecuteResult result = orchestrator.execute(request);
        assertFalse(result.isSuccess());
        assertEquals(1, result.getExecutedSteps(), "必需步骤失败后应停止，不再执行后续步骤");
    }
}
