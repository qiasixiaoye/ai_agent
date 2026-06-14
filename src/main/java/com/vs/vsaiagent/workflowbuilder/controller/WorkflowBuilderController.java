package com.vs.vsaiagent.workflowbuilder.controller;

import com.vs.vsaiagent.dify.client.DifyConsoleClient;
import com.vs.vsaiagent.dify.dto.DifyImportResult;
import com.vs.vsaiagent.workflowbuilder.model.ValidateResult;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowGenerateRequest;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowGenerateResponse;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowIR;
import com.vs.vsaiagent.workflowbuilder.model.WorkflowValidateRequest;
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

/**
 * Workflow Builder REST 接口（context-path /api 下）：
 *
 *  POST /workflow-builder/generate           自然语言 → IR → DSL → 校验
 *  POST /workflow-builder/validate           校验外部传入的 DSL YAML
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

    public WorkflowBuilderController(WorkflowPlanningService planningService,
                                     WorkflowDslGenerateService dslGenerateService,
                                     WorkflowDslValidateService dslValidateService,
                                     WorkflowFileService fileService,
                                     DifyConsoleClient difyConsoleClient) {
        this.planningService = planningService;
        this.dslGenerateService = dslGenerateService;
        this.dslValidateService = dslValidateService;
        this.fileService = fileService;
        this.difyConsoleClient = difyConsoleClient;
    }

    @PostMapping("/generate")
    public WorkflowGenerateResponse generate(@RequestBody WorkflowGenerateRequest request) {
        WorkflowIR ir = planningService.plan(request == null ? null : request.requirement());
        String dslYaml = dslGenerateService.toDslYaml(ir);
        ValidateResult result = dslValidateService.validateDsl(dslYaml);
        if (result.valid()) {
            fileService.save(ir.id(), dslYaml);
        }
        log.info("[workflow-builder] generate name={} valid={} errors={}", ir.name(), result.valid(), result.errors());
        return new WorkflowGenerateResponse(ir.id(), ir.name(), ir, dslYaml, result.valid(), result.errors());
    }

    @PostMapping("/validate")
    public ValidateResult validate(@RequestBody WorkflowValidateRequest request) {
        return dslValidateService.validateDsl(request == null ? null : request.dslYaml());
    }

    @PostMapping("/import/{workflowId}")
    public DifyImportResult importToDify(@PathVariable String workflowId) {
        String yaml = fileService.load(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("workflowId 不存在或 DSL 未生成: " + workflowId));
        DifyImportResult result = difyConsoleClient.importDsl(yaml);
        log.info("[workflow-builder] import workflowId={} success={} appId={}",
                workflowId, result.isSuccess(), result.getAppId());
        return result;
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
