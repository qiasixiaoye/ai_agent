package com.vs.vsaiagent.workflow;

import java.util.Map;

/**
 * 工作流节点。当前 MVP 支持两类：
 *  - "llm"   : 用 prompt 调一次大模型，结果写入 outputVar
 *  - "skill" : 调用 SkillRegistry 中已注册的 Skill
 *
 * prompt / skillName / args 字段视 type 而定，未用到的可为 null。
 * 变量替换语法：${input} 取初始输入，${varname} 取前序节点 outputVar。
 */
public record WorkflowNode(
        String id,
        String type,
        String prompt,
        String skillName,
        Map<String, Object> args,
        String outputVar
) {}
