package com.vs.vsaiagent.observability.controller;

import com.vs.vsaiagent.observability.vo.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ObservabilityController.class)
public class ObservabilityExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handle(Exception e) {
        return ApiResponse.fail(e.getMessage());
    }
}
