package com.vs.vsaiagent.knowledgebase.controller;

import com.vs.vsaiagent.knowledgebase.vo.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = KnowledgeBaseController.class)
public class KnowledgeBaseExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handle(Exception e) {
        return ApiResponse.fail(e.getMessage());
    }
}
