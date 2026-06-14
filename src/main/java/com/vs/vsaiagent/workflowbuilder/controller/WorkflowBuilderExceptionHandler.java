package com.vs.vsaiagent.workflowbuilder.controller;

import com.vs.vsaiagent.workflowbuilder.model.ValidateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Workflow Builder 模块统一异常处理：参数类错误 → 400 + ValidateResult 结构。
 */
@Slf4j
@RestControllerAdvice(assignableTypes = WorkflowBuilderController.class)
public class WorkflowBuilderExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ValidateResult> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ValidateResult.fail(List.of(e.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ValidateResult> handleIllegalState(IllegalStateException e) {
        log.error("[workflow-builder] internal error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ValidateResult.fail(List.of(e.getMessage())));
    }
}
