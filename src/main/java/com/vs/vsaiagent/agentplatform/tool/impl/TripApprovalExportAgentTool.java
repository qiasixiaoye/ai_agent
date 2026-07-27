package com.vs.vsaiagent.agentplatform.tool.impl;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.model.ToolSourceType;
import com.vs.vsaiagent.agentplatform.tool.BaseAgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Simulates an irreversible export after the orchestration confirmation gate has allowed it. */
@Component
public class TripApprovalExportAgentTool extends BaseAgentTool {
    @Override public ToolMetadata metadata() {
        return ToolMetadata.builder().toolName("export_trip_approval").displayName("导出行程审批单")
                .description("仅在服务端确认门禁放行后生成不含联系人明文的演示审批单编号")
                .sourceType(ToolSourceType.LOCAL).tags(List.of("field-research", "export", "irreversible-write"))
                .requiredParams(List.of("location")).timeoutMs(1500L).build();
    }
    @Override public ToolExecuteResult execute(ToolExecuteRequest request) {
        return ToolExecuteResult.builder().toolName(toolName()).success(true)
                .output("APPROVAL-DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                        + "（演示编号，不含联系人明文）").costMs(0L).build();
    }
}
