package com.vs.vsaiagent.workflowbuilder.controller;

import com.vs.vsaiagent.dify.client.DifyConsoleClient;
import com.vs.vsaiagent.dify.dto.DifyDraftRunResult;
import com.vs.vsaiagent.dify.dto.DifyImportResult;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionTraceRecorder;
import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.workflowbuilder.model.DifyRunObserveRequest;
import com.vs.vsaiagent.workflowbuilder.model.ValidateResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowGenerateRequest;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowGenerateResponse;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowNode;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowRunRequest;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowRunResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowValidateRequest;
import com.vs.vsaiagent.workflowbuilder.service.DifyRunObserveService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowBuilderExecutionService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowDslGenerateService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowDslValidateService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowFileService;
import com.vs.vsaiagent.workflowbuilder.service.WorkflowPlanningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Workflow Builder REST 接口（context-path /api 下）：
 *
 *  POST /workflow-builder/generate           自然语言 → IR → DSL → 校验
 *  POST /workflow-builder/validate           校验外部传入的 DSL YAML
 *  POST /workflow-builder/run                本地试运行（不依赖 Dify），返回分步结果
 *  GET  /workflow-builder/export/{workflowId} 导出 generated_workflow.yml
 *  POST /workflow-builder/import/{workflowId} 把已生成的 DSL 通过 Console API 导入本地 Dify
 */
@Slf4j
@RestController
@RequestMapping("/workflow-builder")
public class WorkflowBuilderController {

    private final WorkflowPlanningService planningService;
    private final WorkflowDslGenerateService dslGenerateService;
    private final WorkflowDslValidateService dslValidateService;
    private final WorkflowFileService fileService;
    private final DifyConsoleClient difyConsoleClient;
    private final WorkflowBuilderExecutionService executionService;
    private final DifyRunObserveService difyRunObserveService;
    private final ExecutionTraceRecorder recorder;

    public WorkflowBuilderController(WorkflowPlanningService planningService,
                                     WorkflowDslGenerateService dslGenerateService,
                                     WorkflowDslValidateService dslValidateService,
                                     WorkflowFileService fileService,
                                     DifyConsoleClient difyConsoleClient,
                                     WorkflowBuilderExecutionService executionService,
                                     DifyRunObserveService difyRunObserveService,
                                     ExecutionTraceRecorder recorder) {
        this.planningService = planningService;
        this.dslGenerateService = dslGenerateService;
        this.dslValidateService = dslValidateService;
        this.fileService = fileService;
        this.difyConsoleClient = difyConsoleClient;
        this.executionService = executionService;
        this.difyRunObserveService = difyRunObserveService;
        this.recorder = recorder;
    }

    @PostMapping("/generate")
    public ApiResponse<WorkflowGenerateResponse> generate(@RequestBody WorkflowGenerateRequest request) {
        long t0 = System.currentTimeMillis();
        String mode = request == null ? WorkflowGenerateRequest.MODE_HTTP : request.modeOrDefault();
        String appKind = request == null ? WorkflowGenerateRequest.APP_CHATFLOW : request.appKindOrDefault();
        String requirement = request == null ? null : request.requirement();

        // 起一条审计请求：把规划诊断 / 校验错误落到 observability，事后可按 requestId 检索
        String requestId = recorder.start("workflow-builder-generate",
                java.util.Map.of("requirement", requirement == null ? "" : requirement, "mode", mode, "appKind", appKind),
                null);

        WorkflowPlanningService.PlanResult plan = planningService.planDetailed(requirement);
        WorkflowIR ir = plan.ir();
        String dslYaml = dslGenerateService.toDslYaml(ir, mode, appKind);
        ValidateResult result = dslValidateService.validateDsl(dslYaml);
        if (result.valid()) {
            fileService.save(ir.id(), dslYaml);
        }

        // 诊断：规划器选择 / LLM 回退原因 + 形态信息，连同校验错误一起暴露给调用方（错误分析审计）
        List<String> warnings = new ArrayList<>(plan.diagnostics());
        warnings.add("形态：" + mode + " · " + appKind);
        long toolCount = ir.nodes().stream().filter(n -> WorkflowNode.TYPE_TOOL.equals(n.type())).count();
        if (WorkflowGenerateRequest.MODE_AGENT.equalsIgnoreCase(mode) && toolCount == 0) {
            warnings.add("agent 形态无可挂载工具，已降级为 http 纯 LLM 流程");
        }

        // 落库：规划诊断 + 校验结果各记一条 stage，整体成败按 valid
        recorder.stage(requestId, ExecutionStageType.INPUT, "规划诊断", "planner",
                requirement, String.join(" | ", warnings), null, true, null);
        if (result.valid()) {
            recorder.stage(requestId, ExecutionStageType.OUTPUT, "DSL校验", null,
                    null, "name=" + ir.name() + " nodes=" + ir.nodes().size(), null, true, null);
            recorder.success(requestId, "name=" + ir.name() + " mode=" + mode + " appKind=" + appKind, t0);
        } else {
            String errMsg = String.join("; ", result.errors());
            recorder.stage(requestId, ExecutionStageType.ERROR, "DSL校验", null,
                    null, null, null, false, errMsg);
            recorder.fail(requestId, errMsg, t0);
        }

        log.info("[workflow-builder] generate name={} mode={} appKind={} valid={} errors={} diag={} requestId={}",
                ir.name(), mode, appKind, result.valid(), result.errors(), warnings, requestId);
        return ApiResponse.success(new WorkflowGenerateResponse(
                ir.id(), ir.name(), ir, dslYaml, result.valid(), result.errors(), warnings, requestId));
    }

    @PostMapping("/validate")
    public ApiResponse<ValidateResult> validate(@RequestBody WorkflowValidateRequest request) {
        return ApiResponse.success(dslValidateService.validateDsl(request == null ? null : request.dslYaml()));
    }

    @PostMapping("/run")
    public ApiResponse<WorkflowRunResult> run(@RequestBody WorkflowRunRequest request) {
        if (request == null || request.ir() == null) {
            throw new IllegalArgumentException("ir 不能为空");
        }
        return ApiResponse.success(executionService.run(request.ir(), request.input()));
    }

    /**
     * 在 Dify 里草稿运行已导入的应用并观测运行时轨迹（节点结果 / Agent 轮次 / 错误），结果落 observability。
     * 这是「运行时错误暴露」的入口：以前看不到的运行时故障，现在能在返回轨迹与审计模块里查到。
     */
    @PostMapping("/dify-run")
    public ApiResponse<DifyDraftRunResult> difyRun(@RequestBody DifyRunObserveRequest request) {
        if (request == null || request.appId() == null || request.appId().isBlank()) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        boolean advancedChat = !"workflow".equalsIgnoreCase(request.appKind());
        Map<String, Object> inputs = request.inputs();
        if (!advancedChat && (inputs == null || inputs.isEmpty())) {
            // workflow 形态：start 节点的 input 变量取 query
            inputs = Map.of("input", request.query() == null ? "" : request.query());
        }
        DifyDraftRunResult result = difyRunObserveService.run(request.appId(), advancedChat, request.query(), inputs);
        log.info("[workflow-builder] dify-run appId={} success={} status={} nodes={} requestId={}",
                request.appId(), result.success(), result.status(),
                result.nodes() == null ? 0 : result.nodes().size(), result.requestId());
        return ApiResponse.success(result);
    }

    @PostMapping("/import/{workflowId}")
    public ApiResponse<DifyImportResult> importToDify(@PathVariable String workflowId) {
        String yaml = fileService.load(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("workflowId 不存在或 DSL 未生成: " + workflowId));
        DifyImportResult result = difyConsoleClient.importDsl(yaml);
        log.info("[workflow-builder] import workflowId={} success={} appId={}",
                workflowId, result.isSuccess(), result.getAppId());
        return ApiResponse.success(result);
    }

    @GetMapping("/export/{workflowId}")
    public ResponseEntity<byte[]> export(@PathVariable String workflowId) {
        return fileService.load(workflowId)
                .map(yaml -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"generated_workflow.yml\"")
                        .contentType(MediaType.parseMediaType("application/x-yaml"))
                        .body(yaml.getBytes(StandardCharsets.UTF_8)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
