package com.vs.vsaiagent.workflow.controller;

import com.vs.vsaiagent.eval.CaseResult;
import com.vs.vsaiagent.eval.EvalCase;
import com.vs.vsaiagent.eval.EvalJudge;
import com.vs.vsaiagent.eval.SuiteResult;
import com.vs.vsaiagent.eval.judge.KeywordContainsJudge;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionTraceRecorder;
import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.workflow.StepResult;
import com.vs.vsaiagent.workflow.WorkflowDef;
import com.vs.vsaiagent.workflow.WorkflowResult;
import com.vs.vsaiagent.workflow.runner.WorkflowEvalRunner;
import com.vs.vsaiagent.workflow.service.DifyDslExporter;
import com.vs.vsaiagent.workflow.service.WorkflowExecutor;
import com.vs.vsaiagent.workflow.service.WorkflowGenerator;
import com.vs.vsaiagent.workflow.service.WorkflowRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow REST：一句话生成 + 执行 + 评测 + 导出 Dify YAML。
 *
 *  - POST /workflow/generate              {prompt}                 → WorkflowDef
 *  - GET  /workflow                       → List<WorkflowDef>
 *  - GET  /workflow/{id}                  → WorkflowDef
 *  - POST /workflow/{id}/execute          {input}                  → WorkflowResult
 *  - POST /workflow/{id}/eval             {cases, judge}           → SuiteResult
 *  - GET  /workflow/{id}/dify-dsl         → text/yaml file
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    private final WorkflowGenerator generator;
    private final WorkflowExecutor executor;
    private final WorkflowRegistry registry;
    private final DifyDslExporter difyExporter;
    private final WorkflowEvalRunner workflowEvalRunner;
    private final ExecutionTraceRecorder traceRecorder;
    private final Map<String, EvalJudge> judges = new java.util.HashMap<>();

    public WorkflowController(WorkflowGenerator generator,
                              WorkflowExecutor executor,
                              WorkflowRegistry registry,
                              DifyDslExporter difyExporter,
                              WorkflowEvalRunner workflowEvalRunner,
                              ExecutionTraceRecorder traceRecorder,
                              List<EvalJudge> judgeList) {
        this.generator = generator;
        this.executor = executor;
        this.registry = registry;
        this.difyExporter = difyExporter;
        this.workflowEvalRunner = workflowEvalRunner;
        this.traceRecorder = traceRecorder;
        for (EvalJudge j : judgeList) judges.put(j.name(), j);
    }

    @Data
    public static class GenerateRequest { private String prompt; }

    @Data
    public static class ExecuteRequest { private String input; }

    @Data
    public static class EvalRequest {
        private String judge;
        private List<EvalCaseDto> cases;
    }

    @Data
    public static class EvalCaseDto {
        private String id;
        private String input;
        private List<String> expectedContains;
        private String rubric;
    }

    @PostMapping("/generate")
    public ApiResponse<WorkflowDef> generate(@RequestBody GenerateRequest req) {
        if (req == null || req.getPrompt() == null || req.getPrompt().isBlank()) {
            return ApiResponse.fail("prompt 不能为空");
        }
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("workflow.generate", req.getPrompt(), "workflow-generator");
        try {
            WorkflowDef def = generator.generate(req.getPrompt());
            registry.save(def);
            traceRecorder.stage(requestId, ExecutionStageType.MODEL, "generate_workflow", null,
                    req.getPrompt(), def, System.currentTimeMillis() - startedAt, true, null);
            traceRecorder.success(requestId, def, startedAt);
            log.info("[workflow] generated id={} name={} nodes={}", def.id(), def.name(), def.nodes().size());
            return ApiResponse.success(def);
        } catch (Exception e) {
            log.warn("[workflow] generate failed", e);
            traceRecorder.fail(requestId, e, startedAt);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<WorkflowDef>> list() {
        return ApiResponse.success(registry.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowDef> detail(@PathVariable String id) {
        return registry.find(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail("workflow not found: " + id));
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<WorkflowResult> execute(@PathVariable String id, @RequestBody ExecuteRequest req) {
        WorkflowDef def = registry.find(id).orElse(null);
        if (def == null) return ApiResponse.fail("workflow not found: " + id);
        String input = req == null ? "" : req.getInput();
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("workflow.execute:" + id, input, "workflow");
        try {
            WorkflowResult r = executor.execute(def, input);
            for (StepResult step : r.steps()) {
                ExecutionStageType stageType = "llm".equalsIgnoreCase(step.type())
                        ? ExecutionStageType.MODEL
                        : ExecutionStageType.TOOL;
                traceRecorder.stage(requestId, stageType, "workflow_node:" + step.nodeId(), step.type(),
                        step.input(), step.output(), step.elapsedMs(), step.success(), step.errorMessage());
            }
            if (r.success()) {
                traceRecorder.success(requestId, r, startedAt);
            } else {
                traceRecorder.fail(requestId, r.errorMessage(), startedAt);
            }
            return ApiResponse.success(r);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @PostMapping("/{id}/eval")
    public ApiResponse<SuiteResult> evalOn(@PathVariable String id, @RequestBody EvalRequest req) {
        WorkflowDef def = registry.find(id).orElse(null);
        if (def == null) return ApiResponse.fail("workflow not found: " + id);
        String judgeName = req == null || req.getJudge() == null ? KeywordContainsJudge.NAME : req.getJudge();
        EvalJudge judge = judges.getOrDefault(judgeName, judges.get(KeywordContainsJudge.NAME));
        if (judge == null) return ApiResponse.fail("no judge available");

        long start = System.currentTimeMillis();
        String requestId = traceRecorder.start("workflow.eval:" + id, req, judgeName);
        List<CaseResult> caseResults = new ArrayList<>();
        int pass = 0, fail = 0;

        workflowEvalRunner.setCurrent(id);
        try {
            List<EvalCaseDto> cases = req == null || req.getCases() == null ? List.of() : req.getCases();
            for (EvalCaseDto c : cases) {
                String chatId = "wf-eval-" + id + "-" + UUID.randomUUID();
                EvalCase ec = new EvalCase(c.getId(), c.getInput(),
                        c.getExpectedContains(), c.getRubric(), List.of());

                long rs = System.currentTimeMillis();
                String actual;
                try {
                    actual = workflowEvalRunner.run(c.getInput(), chatId);
                } catch (Exception e) {
                    actual = "[runner error] " + e.getMessage();
                }
                long re = System.currentTimeMillis() - rs;

                long js = System.currentTimeMillis();
                CaseResult cr = judge.judge(ec, actual);
                long je = System.currentTimeMillis() - js;

                CaseResult enriched = new CaseResult(cr.caseId(), cr.input(), cr.actualOutput(),
                        cr.pass(), cr.reason(), cr.missedKeywords(), re, je);
                traceRecorder.stage(requestId, ExecutionStageType.TOOL, "eval_case:" + c.getId(), judge.name(),
                        ec, enriched, re + je, enriched.pass(), enriched.pass() ? null : enriched.reason());
                caseResults.add(enriched);
                if (enriched.pass()) pass++; else fail++;
            }
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, start);
            throw e;
        } finally {
            workflowEvalRunner.clearCurrent();
        }

        SuiteResult sr = new SuiteResult(
                "workflow:" + id,
                WorkflowEvalRunner.NAME,
                judge.name(),
                caseResults.size(),
                pass,
                fail,
                System.currentTimeMillis() - start,
                caseResults
        );
        traceRecorder.success(requestId, sr, start);
        return ApiResponse.success(sr);
    }

    @GetMapping(value = "/{id}/dify-dsl", produces = "application/x-yaml")
    public ResponseEntity<String> exportDify(@PathVariable String id) {
        WorkflowDef def = registry.find(id).orElse(null);
        if (def == null) return ResponseEntity.notFound().build();
        String yaml = difyExporter.toYaml(def);
        String filename = (def.name() == null ? "workflow" : def.name().replaceAll("\\s+", "_")) + ".yaml";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(yaml);
    }
}
