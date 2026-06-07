package com.vs.vsaiagent.eval.controller;

import com.vs.vsaiagent.eval.EvalSuite;
import com.vs.vsaiagent.eval.SuiteResult;
import com.vs.vsaiagent.eval.service.EvalService;
import com.vs.vsaiagent.observability.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Eval REST 接口。
 *
 *  - GET  /eval/suites           列出所有 suite（含 case 数量）
 *  - POST /eval/run/{suiteName}  执行一份 suite，返回 SuiteResult
 */
@Slf4j
@RestController
@RequestMapping("/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/suites")
    public ApiResponse<List<EvalSuite>> listSuites() {
        return ApiResponse.success(evalService.listSuites());
    }

    @PostMapping("/run/{suiteName}")
    public ApiResponse<SuiteResult> run(@PathVariable String suiteName) {
        log.info("[eval-controller] run suite={}", suiteName);
        try {
            return ApiResponse.success(evalService.run(suiteName));
        } catch (Exception e) {
            log.warn("[eval-controller] run failed: {}", e.getMessage());
            return ApiResponse.fail(e.getMessage());
        }
    }
}
