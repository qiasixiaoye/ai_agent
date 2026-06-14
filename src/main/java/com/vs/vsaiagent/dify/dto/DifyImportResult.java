package com.vs.vsaiagent.dify.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DSL 导入 Dify 的结果。
 */
@Data
@Builder
public class DifyImportResult {

    private boolean success;

    /** 导入后生成/更新的 Dify 应用 id */
    private String appId;

    /** Dify 返回的导入状态：completed / completed-with-warnings / pending / failed */
    private String status;

    /** 导入成功后可直接打开的画布地址 */
    private String appUrl;

    private String errorMessage;

    /** Dify 原始响应，便于排查版本字段差异 */
    private String rawResponse;
}
