package com.vs.vsaiagent.agentplatform.controller;

import com.vs.vsaiagent.agentplatform.vo.AgentApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AgentPlatformController.class)
public class AgentPlatformExceptionHandler {

    @ExceptionHandler(Exception.class)
    public AgentApiResponse<Object> handle(Exception e) {
        return AgentApiResponse.fail(e.getMessage());
    }
}
