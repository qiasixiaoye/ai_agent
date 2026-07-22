package com.vs.vsaiagent.skill.controller;

import com.vs.vsaiagent.observability.vo.ApiResponse;
import com.vs.vsaiagent.skill.routing.SkillRouteDecision;
import com.vs.vsaiagent.skill.routing.SkillRouter;
import com.vs.vsaiagent.skill.routing.SkillRoutingEvalReport;
import com.vs.vsaiagent.skill.routing.SkillRoutingEvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供无需调用 LLM 的 Skill 路由预览，便于前端展示、调参与离线评测。 */
@RestController
@RequestMapping("/skills")
public class SkillRoutingController {

    private final SkillRouter skillRouter;
    private final SkillRoutingEvalService evalService;

    public SkillRoutingController(SkillRouter skillRouter, SkillRoutingEvalService evalService) {
        this.skillRouter = skillRouter;
        this.evalService = evalService;
    }

    @GetMapping("/route")
    public ApiResponse<SkillRouteDecision> route(@RequestParam String query,
                                                  @RequestParam(defaultValue = "3") int topK,
                                                  @RequestParam(defaultValue = "0.24") double threshold) {
        return ApiResponse.success(skillRouter.route(query, topK, threshold));
    }

    @GetMapping("/route/evaluate")
    public ApiResponse<SkillRoutingEvalReport> evaluate() {
        return ApiResponse.success(evalService.evaluate());
    }
}
