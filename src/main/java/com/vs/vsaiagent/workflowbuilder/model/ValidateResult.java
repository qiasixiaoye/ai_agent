package com.vs.vsaiagent.workflowbuilder.model;

import java.util.List;

/**
 * 校验结果。
 *
 * @param valid  是否通过
 * @param errors 错误信息列表（通过时为空列表）
 */
public record ValidateResult(boolean valid, List<String> errors) {

    public static ValidateResult ok() {
        return new ValidateResult(true, List.of());
    }

    public static ValidateResult fail(List<String> errors) {
        return new ValidateResult(false, List.copyOf(errors));
    }
}
