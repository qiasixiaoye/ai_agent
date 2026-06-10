package com.vs.vsaiagent.agentplatform.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentApiResponse<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> AgentApiResponse<T> success(T data) {
        return AgentApiResponse.<T>builder().code(0).message("ok").data(data).build();
    }

    public static <T> AgentApiResponse<T> fail(String message) {
        return AgentApiResponse.<T>builder().code(-1).message(message).build();
    }
}
