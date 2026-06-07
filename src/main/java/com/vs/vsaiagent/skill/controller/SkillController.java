package com.vs.vsaiagent.skill.controller;

import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.skill.Skill;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillResult;
import com.vs.vsaiagent.skill.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Skill 平台 REST 接口。
 *
 *  - GET  /skills              列出所有已注册 Skill（轻量）
 *  - GET  /skills/{name}       Skill 详情（含 inputs / outputs / examples）
 *  - POST /skills/{name}/execute   直接执行 Skill
 *
 * 响应统一用 {@link ApiResponse}，与 ObservabilityController 风格保持一致。
 */
@Slf4j
@RestController
@RequestMapping("/skills")
public class SkillController {

    private final SkillRegistry skillRegistry;

    public SkillController(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @GetMapping
    public ApiResponse<List<SkillSummaryVO>> list() {
        List<SkillSummaryVO> data = skillRegistry.listAll().stream()
                .map(s -> SkillSummaryVO.from(s.metadata()))
                .toList();
        return ApiResponse.success(data);
    }

    @GetMapping("/{name}")
    public ApiResponse<SkillDetailVO> detail(@PathVariable String name) {
        return skillRegistry.find(name)
                .map(s -> ApiResponse.success(SkillDetailVO.from(s.metadata())))
                .orElseGet(() -> ApiResponse.fail("skill not found: " + name));
    }

    @PostMapping("/{name}/execute")
    public ApiResponse<SkillResult> execute(@PathVariable String name,
                                            @RequestBody(required = false) Map<String, Object> arguments) {
        Skill skill = skillRegistry.find(name).orElse(null);
        if (skill == null) {
            return ApiResponse.fail("skill not found: " + name);
        }
        SkillContext ctx = SkillContext.builder().build();
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        log.info("[skill-controller] execute skill={} args={}", name, args.keySet());
        SkillResult result = skill.execute(args, ctx);
        return ApiResponse.success(result);
    }
}
