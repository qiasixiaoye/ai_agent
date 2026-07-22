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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 银河拍摄计划（结构化 Skill）。
 *
 * <p>这是一个<b>有内部结构</b>的 Skill，用来直观区分「Skill」与「单个工具/接口」：
 * 一个裸工具是「一次函数调用」；而本 Skill 是一段<b>有序过程</b>——它按 {@link #STEPS} 声明的步骤，
 * 依次<b>在内部编排调用多个已注册的后端工具</b>（milkyway_rise / light_pollution / cloud_cover），
 * 再把各步结果按操作手册综合成一份可执行的拍摄计划。
 *
 * <p>关键点：{@code steps} 既是<b>给人看的内部结构</b>（经 SkillDetailVO 暴露），也是
 * {@code doExecute} 的<b>实际执行计划</b>——执行时遍历这些步骤逐个调用其 {@code uses} 指向的工具，
 * 所以"内部结构"不是装饰，而是真正驱动行为。
 */
@Slf4j
@Component
public class AstroShootPlanSkill extends AbstractSkill {

    public static final String SKILL_NAME = "astro-shoot-plan";

    /** 内部步骤 = 执行计划：每步 uses 指向一个已注册工具，由 doExecute 实际调用。 */
    private static final List<SkillStep> STEPS = List.of(
            SkillStep.of("银河升起时间", "tool:milkyway_rise", "算出银河核心升起/中天/落下时间与方位，确定拍摄时间窗"),
            SkillStep.of("光污染评估", "tool:light_pollution", "估算机位 Bortle 等级，判断是否需要更暗的备选点"),
            SkillStep.of("夜间云量", "tool:cloud_cover", "查当晚云量预报，决定能否成行 / 是否改期"),
            SkillStep.of("综合成拍摄计划", null, "把上面三步结果按操作手册综合成时间窗 + 机位 + 风险提示")
    );

    private final ToolExecutionService toolExecutionService;

    public AstroShootPlanSkill(ToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    protected SkillMetadata defaultMetadata() {
        return SkillMetadata.builder()
                .name(SKILL_NAME)
                .displayName("银河拍摄计划")
                .description("给定经纬度与日期，内部依次编排银河升起/光污染/云量三个工具，综合成一份银河拍摄计划")
                .version("1.0.0")
                .tags(List.of("astro", "photography", "composite", "银河", "银河摄影", "星空摄影", "拍摄计划"))
                .inputs(List.of(
                        SkillParam.required("latitude", "number", "机位纬度，如 39.9"),
                        SkillParam.required("longitude", "number", "机位经度，如 116.4"),
                        SkillParam.required("date", "string", "拍摄日期 yyyy-MM-dd")
                ))
                .outputs(List.of(
                        SkillParam.optional("plan", "string", "综合拍摄计划", null)
                ))
                .steps(STEPS)
                .examples(List.of("纬度39.9 经度116.4 2026-06-25 的银河拍摄计划"))
                .timeoutMs(20000L)
                .sourceType(SkillSourceType.LOCAL)
                .build();
    }

    @Override
    protected Object doExecute(Map<String, Object> arguments, SkillContext context) {
        Object lat = arguments.get("latitude");
        Object lon = arguments.get("longitude");
        Object date = arguments.get("date");

        StringBuilder plan = new StringBuilder("【银河拍摄计划】\n");
        Map<String, String> stepOutputs = new LinkedHashMap<>();

        // 遍历声明的内部步骤，逐个调用其 uses 指向的工具：steps 即执行计划。
        for (SkillStep step : STEPS) {
            if (step.uses() == null || !step.uses().startsWith("tool:")) {
                continue;   // 综合步在循环后做
            }
            String toolName = step.uses().substring("tool:".length());
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("latitude", lat);
            args.put("longitude", lon);
            if (!"light_pollution".equals(toolName)) {
                args.put("date", date);   // light_pollution 不需要 date
            }
            ToolExecuteResult r = toolExecutionService.executeByName(ToolExecuteRequest.builder()
                    .toolName(toolName)
                    .arguments(args)
                    .traceId(context == null ? null : context.traceId())
                    .build());
            String out = r != null && r.isSuccess() ? r.getOutput()
                    : ("（" + toolName + " 调用失败：" + (r == null ? "no result" : r.getErrorMessage()) + "）");
            stepOutputs.put(step.name(), out);
            plan.append("\n■ ").append(step.name()).append("\n").append(out).append("\n");
        }

        // 综合步：基于各步结果给出建议（纯文本规则综合，演示稳定）。
        plan.append("\n■ 综合建议\n")
            .append("· 在【银河升起时间】给出的时间窗内拍摄，避开月光与中天前后的城市方位；\n")
            .append("· 若【光污染评估】等级偏高(Bortle≥5)，优先换到更暗的备选机位；\n")
            .append("· 若【夜间云量】偏高(>50%)，建议改期或准备备用题材(地景/星轨)。");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan.toString());
        result.put("steps", new ArrayList<>(stepOutputs.keySet()));   // 回传实际执行过的步骤，便于审计/前端展示
        return result;
    }
}
