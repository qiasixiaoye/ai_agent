package com.vs.vsaiagent.workflowbuilder;

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

    private final WorkflowPlanningService planning = new WorkflowPlanningService();
    private final WorkflowDslGenerateService dslGenerate = new WorkflowDslGenerateService("tongyi", "qwen-max");
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
        assertTrue(yaml.contains("mode: workflow"));
        assertTrue(yaml.contains("type: start"));
        assertTrue(yaml.contains("type: llm"));
        assertTrue(yaml.contains("type: answer"));
        assertTrue(yaml.contains("{{#start.input#}}"), "llm user 消息应引用 start 输入变量");
        assertTrue(yaml.contains("{{#llm_task.text#}}"), "answer 应引用 llm 输出");

        ValidateResult result = validator.validateDsl(yaml);
        assertTrue(result.valid(), "生成的 DSL 必须自校验通过, errors=" + result.errors());
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
