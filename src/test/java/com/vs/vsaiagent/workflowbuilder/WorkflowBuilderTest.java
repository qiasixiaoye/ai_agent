package com.vs.vsaiagent.workflowbuilder;

import com.vs.vsaiagent.agentplatform.registry.InMemoryToolRegistry;
import com.vs.vsaiagent.skill.registry.InMemorySkillRegistry;
import com.vs.vsaiagent.workflowbuilder.model.ValidateResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowEdge;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowDslGenerateService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowDslValidateService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowFileService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowPlanningService;
import com.vs.vsaiagent.workflowbuilder.util.YamlUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow Builder 纯单元测试（不启动 Spring 容器、不依赖大模型）。
 */
class WorkflowBuilderTest {

    private final WorkflowPlanningService planning = new WorkflowPlanningService(new InMemoryToolRegistry(), new InMemorySkillRegistry());
    private final WorkflowDslGenerateService dslGenerate = new WorkflowDslGenerateService("tongyi", "qwen-max", "http://localhost:8081/api");
    private final WorkflowDslValidateService validator = new WorkflowDslValidateService();

    // ---------- 样例需求（计划书三类内置任务） ----------
    private static final String CASE_SUMMARY = "输入一段文本，生成三条摘要。";
    private static final String CASE_EXTRACT_JD = "输入招聘 JD，提取岗位、地点、技能要求，输出 JSON。";
    private static final String CASE_EXTRACT_PAPER = "输入论文摘要，提取研究问题、方法、数据集和结论。";

    // ---------- 1. IR 生成 ----------

    @Test
    void planGeneratesLegalStartLlmAnswerIr() {
        for (String requirement : List.of(CASE_SUMMARY, CASE_EXTRACT_JD, CASE_EXTRACT_PAPER)) {
            WorkflowIR ir = planning.plan(requirement);
            assertEquals(3, ir.nodes().size());
            assertEquals(WorkflowNode.TYPE_START, ir.nodes().get(0).type());
            assertEquals(WorkflowNode.TYPE_LLM, ir.nodes().get(1).type());
            assertEquals(WorkflowNode.TYPE_ANSWER, ir.nodes().get(2).type());
            assertEquals(2, ir.edges().size());
            assertTrue(ir.nodes().get(1).instruction().contains(requirement), "instruction 应包含原始需求");
            assertTrue(validator.validateIr(ir).valid(), "规则生成的 IR 必须通过校验");
        }
    }

    @Test
    void planNamesByKeywordRules() {
        assertEquals("文本总结工作流", planning.plan(CASE_SUMMARY).name());
        assertEquals("信息提取工作流", planning.plan(CASE_EXTRACT_JD).name());
        assertEquals("文档问答工作流", planning.plan("基于知识库回答用户问题").name());
    }

    @Test
    void planRejectsBlankRequirement() {
        assertThrows(IllegalArgumentException.class, () -> planning.plan("  "));
    }

    // ---------- 2. DSL 生成 ----------

    @Test
    void dslIsParseableAndContainsDifySkeleton() {
        WorkflowIR ir = planning.plan(CASE_EXTRACT_JD);
        String yaml = dslGenerate.toDslYaml(ir);

        Map<String, Object> root = YamlUtil.parse(yaml);
        assertEquals("app", root.get("kind"));
        assertTrue(root.containsKey("workflow"));
        // 默认 toDslYaml(ir) = http 编排 + workflow(单轮) → app.mode=workflow，end 节点收尾
        assertTrue(yaml.contains("mode: workflow"));
        assertTrue(yaml.contains("type: start"));
        assertTrue(yaml.contains("type: llm"));
        assertTrue(yaml.contains("type: end"), "workflow 形态应以 end 节点收尾（answer 属于 chatflow）");
        assertFalse(yaml.contains("type: answer"), "workflow 形态不应出现 answer 节点");
        assertTrue(yaml.contains("{{#start.input#}}"), "llm user 消息应引用 start 输入变量");
        assertTrue(yaml.contains("value_selector"), "end 节点应通过 value_selector 暴露输出");
        assertTrue(yaml.contains("llm_task"), "end 输出应引用 llm 节点");

        ValidateResult result = validator.validateDsl(yaml);
        assertTrue(result.valid(), "生成的 DSL 必须自校验通过, errors=" + result.errors());
    }

    @Test
    @SuppressWarnings("unchecked")
    void parallelToolsProduceFanOutAndAggregator() {
        // 手工构造含两个 tool 的 IR（规则规划器只会插一个工具，故此处直接拼装）
        WorkflowNode start = WorkflowNode.start();
        WorkflowNode t1 = WorkflowNode.tool("tool_call_1", "网页搜索", "tool:web_search", "{\"query\": \"${start}\"}");
        WorkflowNode t2 = WorkflowNode.tool("tool_call_2", "图片搜索", "tool:image_search", "{\"query\": \"${start}\"}");
        WorkflowNode llm = WorkflowNode.llm("llm_task", "并行测试", "汇总两个工具结果");
        WorkflowNode answer = WorkflowNode.answer();
        WorkflowIR ir = new WorkflowIR("pid", "并行测试", "并行汇聚需求",
                List.of(start, t1, t2, llm, answer),
                List.of(new WorkflowEdge("start", "tool_call_1"),
                        new WorkflowEdge("tool_call_1", "tool_call_2"),
                        new WorkflowEdge("tool_call_2", "llm_task"),
                        new WorkflowEdge("llm_task", "answer")));

        String yaml = dslGenerate.toDslYaml(ir, "http", "workflow");
        assertTrue(yaml.contains("type: template-transform"), "≥2 工具应生成汇聚节点");
        assertTrue(validator.validateDsl(yaml).valid(), "并行 DSL 必须自校验通过");

        Map<String, Object> root = YamlUtil.parse(yaml);
        Map<String, Object> graph = (Map<String, Object>) ((Map<String, Object>) root.get("workflow")).get("graph");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        long fanOut = edges.stream().filter(e -> "start".equals(e.get("source"))).count();
        long fanIn = edges.stream().filter(e -> "aggregate".equals(e.get("target"))).count();
        assertEquals(2, fanOut, "start 应 fan-out 到 2 个工具（并行）");
        assertEquals(2, fanIn, "2 个工具应 fan-in 到汇聚节点");
    }

    @Test
    @SuppressWarnings("unchecked")
    void manyToolsAgentModeProducesParallelMultiAgent() {
        // agent 形态需注册表映射 MCP 工具，故用带 InMemory 注册表的完整构造器
        WorkflowDslGenerateService agentGen = new WorkflowDslGenerateService(
                new InMemoryToolRegistry(), new InMemorySkillRegistry(),
                "tongyi", "qwen-max", "http://localhost:8081/api",
                "vs-agent", "vs-agent-tools", "langgenius/agent/agent",
                "function_calling", "FunctionCalling", "langgenius/agent:0.0.39@x", 5);

        List<WorkflowNode> ns = new ArrayList<>();
        List<WorkflowEdge> es = new ArrayList<>();
        ns.add(WorkflowNode.start());
        String prev = "start";
        for (int i = 1; i <= 4; i++) {   // 4 个工具 → 触发多 Agent（每组 2 个）
            String id = "tool_call_" + i;
            ns.add(WorkflowNode.tool(id, "工具" + i, "tool:web_search", "{\"query\": \"${start}\"}"));
            es.add(new WorkflowEdge(prev, id));
            prev = id;
        }
        ns.add(WorkflowNode.llm("llm_task", "多智能体", "综合各智能体结果"));
        es.add(new WorkflowEdge(prev, "llm_task"));
        ns.add(WorkflowNode.answer());
        es.add(new WorkflowEdge("llm_task", "answer"));
        WorkflowIR ir = new WorkflowIR("id", "多智能体", "需求", ns, es);

        String yaml = agentGen.toDslYaml(ir, "agent", "chatflow");
        assertTrue(validator.validateDsl(yaml).valid(), "多 Agent DSL 必须自校验通过");

        Map<String, Object> root = YamlUtil.parse(yaml);
        Map<String, Object> graph = (Map<String, Object>) ((Map<String, Object>) root.get("workflow")).get("graph");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        long agentCount = nodes.stream().filter(n -> "agent".equals(((Map<String, Object>) n.get("data")).get("type"))).count();
        boolean hasAgg = nodes.stream().anyMatch(n -> "template-transform".equals(((Map<String, Object>) n.get("data")).get("type")));
        assertEquals(2, agentCount, "应拆成 2 个并行 Agent 节点");
        assertTrue(hasAgg, "应有汇聚节点");

        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        long startFanout = edges.stream().filter(e -> "start".equals(e.get("source"))).count();
        assertEquals(2, startFanout, "start 应并行 fan-out 到 2 个 Agent");
    }

    @Test
    void jsonRequirementAddsJsonOutputConstraint() {
        WorkflowIR ir = planning.plan(CASE_EXTRACT_JD);
        assertTrue(ir.nodes().get(1).instruction().contains("只输出合法 JSON"));
    }

    // ---------- 3-7. 校验用例 ----------

    @Test
    void detectMissingStart() {
        WorkflowIR ir = new WorkflowIR("id", "n", "d",
                List.of(WorkflowNode.llm("llm_task", "t", "i"), WorkflowNode.answer()),
                List.of(new WorkflowEdge("llm_task", "answer")));
        ValidateResult r = validator.validateIr(ir);
        assertFalse(r.valid());
        assertTrue(r.errors().contains("缺少 start 节点"));
    }

    @Test
    void detectMissingAnswer() {
        WorkflowIR ir = new WorkflowIR("id", "n", "d",
                List.of(WorkflowNode.start(), WorkflowNode.llm("llm_task", "t", "i")),
                List.of(new WorkflowEdge("start", "llm_task")));
        ValidateResult r = validator.validateIr(ir);
        assertFalse(r.valid());
        assertTrue(r.errors().contains("缺少 answer 节点"));
    }

    @Test
    void detectDuplicateNodeId() {
        WorkflowIR ir = new WorkflowIR("id", "n", "d",
                List.of(WorkflowNode.start(),
                        WorkflowNode.llm("llm_task", "t", "i"),
                        WorkflowNode.llm("llm_task", "t2", "i2"),
                        WorkflowNode.answer()),
                List.of(new WorkflowEdge("start", "llm_task"), new WorkflowEdge("llm_task", "answer")));
        ValidateResult r = validator.validateIr(ir);
        assertFalse(r.valid());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("节点 id 重复")));
    }

    @Test
    void detectEdgeToMissingNode() {
        WorkflowIR ir = new WorkflowIR("id", "n", "d",
                List.of(WorkflowNode.start(), WorkflowNode.llm("llm_task", "t", "i"), WorkflowNode.answer()),
                List.of(new WorkflowEdge("start", "ghost"), new WorkflowEdge("llm_task", "answer")));
        ValidateResult r = validator.validateIr(ir);
        assertFalse(r.valid());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("不存在的节点")));
    }

    @Test
    void detectCycle() {
        WorkflowIR ir = new WorkflowIR("id", "n", "d",
                List.of(WorkflowNode.start(), WorkflowNode.llm("llm_task", "t", "i"), WorkflowNode.answer()),
                List.of(new WorkflowEdge("start", "llm_task"),
                        new WorkflowEdge("llm_task", "answer"),
                        new WorkflowEdge("answer", "llm_task")));
        ValidateResult r = validator.validateIr(ir);
        assertFalse(r.valid());
        assertTrue(r.errors().contains("工作流图中存在环"));
    }

    @Test
    void validateDslRejectsBrokenYaml() {
        assertFalse(validator.validateDsl("not: [valid").valid());
        assertFalse(validator.validateDsl("   ").valid());
        assertFalse(validator.validateDsl("just a string").valid());
        assertFalse(validator.validateDsl("app: {}").valid(), "缺少 workflow 块应失败");
    }

    // ---------- 文件落盘 ----------

    @Test
    void fileServiceSaveAndLoadRoundTrip(@TempDir Path tempDir) {
        WorkflowFileService fileService = new WorkflowFileService(tempDir);
        String id = UUID.randomUUID().toString();
        fileService.save(id, "kind: app\n");
        assertEquals("kind: app\n", fileService.load(id).orElseThrow());
        assertTrue(fileService.load(UUID.randomUUID().toString()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> fileService.save("../evil", "x"));
    }
}
