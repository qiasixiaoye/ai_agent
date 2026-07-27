package com.vs.vsaiagent.orchestration;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.builtin.EnterpriseFieldResearchSkill;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic adapter from the demo query to the governed enterprise skill and export gate. */
@Component
public class EnterpriseFieldResearchDemoService {

    private final EnterpriseFieldResearchSkill skill;
    private final ToolExecutionService tools;
    private final FieldResearchConfirmationService confirmations;

    public EnterpriseFieldResearchDemoService(EnterpriseFieldResearchSkill skill, ToolExecutionService tools,
                                              FieldResearchConfirmationService confirmations) {
        this.skill = skill;
        this.tools = tools;
        this.confirmations = confirmations;
    }

    public boolean supports(String message) {
        String value = message == null ? "" : message;
        return value.contains("外勤") && (value.contains("调研") || value.contains("客户"));
    }

    public Result execute(String message, String conversationId, String confirmationToken, String traceId) {
        String observedAt = Instant.now().toString();
        List<FieldResearchEvidence> evidence = new ArrayList<>();
        boolean asksExport = message != null && message.contains("导出");
        boolean exportConfirmed = !asksExport || confirmations.consume(conversationId, confirmationToken);
        if (asksExport && !exportConfirmed) {
            String token = confirmations.issue(conversationId);
            evidence.add(new FieldResearchEvidence("export_trip_approval", "blocked", "服务端确认门禁", observedAt,
                    "审批单导出尚未执行", "需要用户确认；确认令牌已发出且仅对当前会话有效"));
            return new Result("结论：已完成只读调研建议的准备，但含联系方式的审批单导出需要你确认后才会执行。", evidence, token);
        }

        SkillResult result = skill.execute(Map.of("location", locationOf(message), "objective", "客户外勤调研"),
                SkillContext.empty());
        Map<?, ?> data = result.data() instanceof Map<?, ?> map ? map : Map.of();
        String weatherStatus = valueOf(data, "weatherStatus", "unavailable");
        String transitStatus = valueOf(data, "transitStatus", "unavailable");
        evidence.add(new FieldResearchEvidence("field_weather_summary", weatherStatus, "演示数据", observedAt,
                "上午与下午外勤天气建议", weatherStatus.equals("available") ? "" : "天气工具不可用，未推断天气"));
        evidence.add(new FieldResearchEvidence("public_transit_risk", transitStatus, "演示数据", observedAt,
                "公共出行缓冲建议", transitStatus.equals("available") ? "" : "出行风险工具不可用，未判断风险等级"));
        evidence.add(new FieldResearchEvidence("field_research_brief", result.success() ? "available" : "unavailable",
                "企业外勤调研 Skill", observedAt, "只根据列出证据汇总", result.success() ? "" : result.errorMessage()));
        String answer = valueOf(data, "brief", "unavailable: 企业外勤调研 Skill 未生成建议");
        if (asksExport) {
            ToolExecuteResult export = tools.executeByName(ToolExecuteRequest.builder().toolName("export_trip_approval")
                    .traceId(traceId).arguments(Map.of("location", locationOf(message))).build());
            evidence.add(new FieldResearchEvidence("export_trip_approval", export.isSuccess() ? "available" : "unavailable",
                    "受确认保护的演示导出", observedAt, export.isSuccess() ? export.getOutput() : "审批单未生成",
                    export.isSuccess() ? "" : export.getErrorMessage()));
            answer += "\n\n审批单：" + (export.isSuccess() ? export.getOutput() : "未生成：" + export.getErrorMessage());
        }
        return new Result(answer, evidence, null);
    }

    private String locationOf(String message) {
        return message != null && message.contains("朝阳") ? "北京朝阳区" : "北京";
    }

    private String valueOf(Map<?, ?> data, String key, String fallback) {
        Object value = data.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public record Result(String answer, List<FieldResearchEvidence> evidence, String confirmationToken) { }
}
