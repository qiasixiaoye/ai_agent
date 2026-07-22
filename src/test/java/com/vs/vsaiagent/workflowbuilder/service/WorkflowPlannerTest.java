package com.vs.vsaiagent.workflowbuilder.service;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.registry.InMemoryToolRegistry;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import com.vs.vsaiagent.skill.registry.InMemorySkillRegistry;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * E1：LLM 规划器的"确定性拼装"逻辑 + 规划服务的"LLM 优先 / 规则兜底"分流。
 * 不依赖真实大模型（assemble 直接喂 LlmPlan；分流用 stub planner）。
 */
class WorkflowPlannerTest {

    private final InMemoryToolRegistry toolRegistry = new InMemoryToolRegistry();
    private final InMemorySkillRegistry skillRegistry = new InMemorySkillRegistry();
    private final WorkflowDslValidateService validator = new WorkflowDslValidateService();

    private LlmWorkflowPlanner newPlanner() {
        // ChatModel 仅用于构造 ChatClient，本测试不触发实际调用
        return new LlmWorkflowPlanner(mock(ChatModel.class), toolRegistry, skillRegistry, validator);
    }

    /** 注册一个最小可用的 web_search 工具，仅供规划拼装查询元数据用。 */
    private void registerWebSearch() {
        toolRegistry.register(new BaseAgentTool() {
            @Override
            public ToolMetadata metadata() {
                return ToolMetadata.builder()
                        .toolName("web_search")
                        .displayName("WebSearch")
                        .description("网页检索")
                        .sourceType(ToolSourceType.LOCAL)
                        .requiredParams(List.of("query"))
                        .timeoutMs(10000L)
                        .build();
            }

            @Override
            public ToolExecuteResult execute(ToolExecuteRequest request) {
                return ToolExecuteResult.builder().toolName("web_search").success(true).output("x").build();
            }
        });
    }

    // ---------- assemble：LLM 决策 → 合法 IR ----------

    @Test
    void assembleProducesValidLinearIrWithoutCapabilities() {
        LlmWorkflowPlanner planner = newPlanner();
        WorkflowIR ir = planner.assemble(
                new LlmWorkflowPlanner.LlmPlan("研究助手", List.of(), "总结输入要点"), "帮我总结一段文字");

        assertEquals(3, ir.nodes().size());
        assertEquals(WorkflowNode.TYPE_START, ir.nodes().get(0).type());
        assertEquals(WorkflowNode.TYPE_LLM, ir.nodes().get(1).type());
        assertEquals(WorkflowNode.TYPE_ANSWER, ir.nodes().get(2).type());
        assertEquals("研究助手", ir.name());
        assertTrue(validator.validateIr(ir).valid(), "拼装出的 IR 必须通过图校验");
    }

    @Test
    void assembleInsertsRegisteredToolNodeAndWiresVariable() {
        registerWebSearch();
        LlmWorkflowPlanner planner = newPlanner();
        WorkflowIR ir = planner.assemble(
                new LlmWorkflowPlanner.LlmPlan("联网总结",
                        List.of(new LlmWorkflowPlanner.CapabilityCall("tool:web_search", Map.of())),
                        "结合搜索结果总结"),
                "联网搜索并总结");

        // start -> tool -> llm -> answer
        assertEquals(4, ir.nodes().size());
        WorkflowNode tool = ir.nodes().get(1);
        assertEquals(WorkflowNode.TYPE_TOOL, tool.type());
        assertEquals("tool:web_search", tool.toolRef());
        assertTrue(tool.instruction().contains("${start}"), "工具参数模板应引用起始输入");
        // llm 指令应引用工具节点输出变量
        WorkflowNode llm = ir.nodes().get(2);
        assertTrue(llm.instruction().contains("${" + tool.id() + "}"), "LLM 指令应引用工具结果变量");
        assertTrue(validator.validateIr(ir).valid());
    }

    @Test
    void assembleDropsUnregisteredCapability() {
        LlmWorkflowPlanner planner = newPlanner();
        // 未注册的工具应被丢弃，不进入 IR（防止大模型臆造能力）
        WorkflowIR ir = planner.assemble(
                new LlmWorkflowPlanner.LlmPlan("x",
                        List.of(new LlmWorkflowPlanner.CapabilityCall("tool:does_not_exist", Map.of())),
                        "做点什么"), "需求");
        assertEquals(3, ir.nodes().size(), "未注册能力应被丢弃，只剩 start/llm/answer");
        assertTrue(validator.validateIr(ir).valid());
    }

    // ---------- 分流：LLM 优先 / 规则兜底 ----------

    @Test
    void planningServiceUsesLlmResultWhenPresent() {
        WorkflowIR preset = newPlanner()
                .assemble(new LlmWorkflowPlanner.LlmPlan("LLM产物", List.of(), "x"), "需求");
        WorkflowPlanner stub = requirement -> Optional.of(preset);

        WorkflowPlanningService service =
                new WorkflowPlanningService(toolRegistry, skillRegistry, stub, "llm");
        WorkflowIR result = service.plan("帮我做个总结工作流");
        assertSame(preset, result, "LLM 规划成功时应直接采用其产物");
    }

    @Test
    void planningServiceFallsBackToRuleWhenLlmEmpty() {
        WorkflowPlanner emptyStub = requirement -> Optional.empty();
        WorkflowPlanningService service =
                new WorkflowPlanningService(toolRegistry, skillRegistry, emptyStub, "llm");

        // 规则命名：包含"总结"→"文本总结工作流"，证明回退到规则规划
        WorkflowIR result = service.plan("请输入一段文本，生成三条摘要总结");
        assertEquals("文本总结工作流", result.name());
        assertTrue(validator.validateIr(result).valid());
    }
}
