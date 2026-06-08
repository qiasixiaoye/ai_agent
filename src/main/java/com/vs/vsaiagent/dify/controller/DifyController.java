package com.vs.vsaiagent.dify.controller;

import com.vs.vsaiagent.dify.client.DifyClient;
import com.vs.vsaiagent.dify.config.DifyProperties;
import com.vs.vsaiagent.dify.dto.DifyRunRequest;
import com.vs.vsaiagent.dify.dto.DifyRunResult;
import com.vs.vsaiagent.observability.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Dify 编排 REST：
 *  - GET  /dify/health         返回 Dify 配置/连通状态
 *  - POST /dify/run            触发 workflow（支持覆盖 workflowId / inputs）
 */
@Slf4j
@RestController
@RequestMapping("/dify")
public class DifyController {

    private final DifyClient difyClient;

    public DifyController(DifyClient difyClient) {
        this.difyClient = difyClient;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        DifyProperties p = difyClient.getProps();
        Map<String, Object> data = new HashMap<>();
        data.put("configured", p.isConfigured());
        data.put("baseUrl", p.getBaseUrl());
        data.put("defaultWorkflowId", p.getDefaultWorkflowId());
        data.put("apiKeyConfigured", p.getApiKey() != null && !p.getApiKey().isBlank());
        return ApiResponse.success(data);
    }

    @PostMapping("/run")
    public ApiResponse<DifyRunResult> run(@RequestBody DifyRunRequest req) {
        log.info("[dify-controller] run workflow={} inputs={}",
                req.getWorkflowId(), req.getInputs() == null ? 0 : req.getInputs().size());
        DifyRunResult result = difyClient.run(req);
        return result.isSuccess()
                ? ApiResponse.success(result)
                : ApiResponse.success(result);  // 失败也作为 data 返回，让前端展示失败原因而非吞掉
    }
}
