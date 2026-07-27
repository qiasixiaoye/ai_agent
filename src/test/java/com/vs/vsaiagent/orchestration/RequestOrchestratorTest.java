package com.vs.vsaiagent.orchestration;

import com.vs.vsaiagent.app.AssistantApp;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.skill.builtin.EnterpriseFieldResearchSkill;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestOrchestratorTest {

    @Test
    void emitsTraceableFinalAnswerEventsWithoutStepText() {
        AssistantApp assistantApp = mock(AssistantApp.class);
        when(assistantApp.doChatByStream("hello", "conversation-1"))
                .thenReturn(Flux.just("答", "案"));
        RequestOrchestrator orchestrator = new RequestOrchestrator(assistantApp, new CapabilityRouter());

        List<OrchestrationEvent> events = orchestrator.stream(
                        new OrchestrationRequest("conversation-1", "hello", null, "request-1", "trace-1"))
                .map(event -> event.data())
                .collectList()
                .block();

        assertEquals(List.of("request_started", "route_selected", "final_delta", "final_delta", "final_completed"),
                events.stream().map(OrchestrationEvent::type).toList());
        assertEquals("答", events.get(2).payload().get("text"));
        assertEquals("案", events.get(3).payload().get("text"));
        assertFalse(events.get(4).payload().containsKey("rawToolOutput"));
    }

    @Test
    void marksKnowledgeRetrievalAsTypedLifecycleEvents() {
        AssistantApp assistantApp = mock(AssistantApp.class);
        when(assistantApp.doChatWithRagSse("从知识库检索 Agent 工具治理", "conversation-2"))
                .thenReturn(Flux.just("知识", "结果"));
        RequestOrchestrator orchestrator = new RequestOrchestrator(assistantApp, new CapabilityRouter());

        List<String> types = orchestrator.stream(new OrchestrationRequest(
                        "conversation-2", "从知识库检索 Agent 工具治理", null, "request-2", "trace-2"))
                .map(event -> event.data().type())
                .collectList()
                .block();

        assertEquals(List.of("request_started", "route_selected", "retrieval_started",
                "final_delta", "final_delta", "retrieval_completed", "final_completed"), types);
    }

    @Test
    void emitsEvidenceAndBlocksExportUntilServerConfirmation() {
        AssistantApp assistantApp = mock(AssistantApp.class);
        ToolExecutionService tools = new ToolExecutionService() {
            @Override public ToolExecuteResult executeByName(ToolExecuteRequest request) {
                return ToolExecuteResult.builder().toolName(request.getToolName()).success(true)
                        .output(request.getToolName() + " evidence").build();
            }
            @Override public ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request) { return executeByName(request); }
            @Override public List<ToolMetadata> listTools() { return List.of(); }
        };
        EnterpriseFieldResearchDemoService demo = new EnterpriseFieldResearchDemoService(
                new EnterpriseFieldResearchSkill(tools), tools, new FieldResearchConfirmationService());
        RequestOrchestrator orchestrator = new RequestOrchestrator(assistantApp, new CapabilityRouter(), demo);

        List<OrchestrationEvent> events = orchestrator.stream(new OrchestrationRequest("conversation-demo",
                        "北京朝阳区客户外勤调研，请导出审批单", null, "request-demo", "trace-demo"))
                .map(event -> event.data()).collectList().block();

        assertTrue(events.stream().anyMatch(event -> "evidence_recorded".equals(event.type())));
        assertTrue(events.stream().anyMatch(event -> "confirmation_required".equals(event.type())));
        assertFalse(events.stream().anyMatch(event -> "rawToolOutput".equals(event.type())));
    }
}
