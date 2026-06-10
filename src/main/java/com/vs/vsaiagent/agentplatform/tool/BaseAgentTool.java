package com.vs.vsaiagent.agentplatform.tool;

import cn.hutool.core.util.StrUtil;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;

import java.util.List;
import java.util.Map;

public abstract class BaseAgentTool implements AgentTool {

    @Override
    public void validate(ToolExecuteRequest request) {
        ToolMetadata metadata = metadata();
        Map<String, Object> args = request.getArguments();
        List<String> requiredParams = metadata.getRequiredParams();
        if (requiredParams == null || requiredParams.isEmpty()) {
            return;
        }
        for (String requiredParam : requiredParams) {
            Object value = args == null ? null : args.get(requiredParam);
            if (value == null || (value instanceof String str && StrUtil.isBlank(str))) {
                throw new IllegalArgumentException("参数缺失: " + requiredParam + ", tool=" + metadata.getToolName());
            }
        }
    }
}
