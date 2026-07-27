package com.vs.vsaiagent.skill.builtin;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.skill.AbstractSkill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillMetadata;
import com.vs.vsaiagent.skill.SkillParam;
import com.vs.vsaiagent.skill.SkillSourceType;
import com.vs.vsaiagent.skill.SkillStep;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A deterministic enterprise demonstration skill with an explicit tool allow-list. */
@Component
public class EnterpriseFieldResearchSkill extends AbstractSkill {

    public static final String SKILL_NAME = "enterprise-field-research";
    private static final List<String> ALLOWED_TOOLS = List.of(
            "field_weather_summary", "public_transit_risk", "field_research_brief");

    private final ToolExecutionService tools;

    public EnterpriseFieldResearchSkill(ToolExecutionService tools) {
        this.tools = tools;
    }

    @Override
    protected SkillMetadata defaultMetadata() {
        return SkillMetadata.builder().name(SKILL_NAME).displayName("企业外勤调研")
                .description("在固定白名单内汇总天气与公共出行演示证据，形成可追溯的外勤建议")
                .version("1.0.0").sourceType(SkillSourceType.LOCAL)
                .tags(List.of("enterprise", "field-research", "travel"))
                .inputs(List.of(SkillParam.required("location", "string", "外勤地点"),
                        SkillParam.required("objective", "string", "调研目标")))
                .outputs(List.of(SkillParam.optional("brief", "string", "外勤调研建议", null)))
                .steps(List.of(SkillStep.of("天气摘要", "tool:field_weather_summary", "读取演示天气证据"),
                        SkillStep.of("公共出行风险", "tool:public_transit_risk", "读取演示出行证据"),
                        SkillStep.of("汇总建议", "tool:field_research_brief", "仅依据上述证据形成建议")))
                .examples(List.of("北京朝阳区客户调研，给出上午和下午建议"))
                .timeoutMs(5000L).build();
    }

    @Override
    protected Object doExecute(Map<String, Object> arguments, SkillContext context) {
        String location = String.valueOf(arguments.get("location"));
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("location", location);
        ToolExecuteResult weather = call(ALLOWED_TOOLS.get(0), base, context);
        ToolExecuteResult transit = call(ALLOWED_TOOLS.get(1), base, context);
        Map<String, Object> briefArgs = Map.of(
                "weatherEvidence", evidence(weather),
                "transitEvidence", evidence(transit));
        ToolExecuteResult brief = call(ALLOWED_TOOLS.get(2), briefArgs, context);
        return Map.of("brief", evidence(brief), "tools", ALLOWED_TOOLS,
                "weatherStatus", weather.isSuccess() ? "available" : "unavailable",
                "transitStatus", transit.isSuccess() ? "available" : "unavailable");
    }

    private ToolExecuteResult call(String toolName, Map<String, Object> args, SkillContext context) {
        if (!ALLOWED_TOOLS.contains(toolName)) {
            throw new IllegalArgumentException("tool is outside enterprise field-research allow-list: " + toolName);
        }
        return tools.executeByName(ToolExecuteRequest.builder().toolName(toolName).arguments(args)
                .traceId(context == null ? null : context.traceId()).build());
    }

    private String evidence(ToolExecuteResult result) {
        if (result == null) return "unavailable: tool returned no result";
        return result.isSuccess() ? result.getOutput() : "unavailable: " + result.getErrorMessage();
    }
}
