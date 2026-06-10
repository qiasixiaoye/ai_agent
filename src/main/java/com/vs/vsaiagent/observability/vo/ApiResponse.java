package com.vs.vsaiagent.observability.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiResponse<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder().code(0).message("ok").data(data).build();
    }

    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder().code(-1).message(message).build();
    }
}
