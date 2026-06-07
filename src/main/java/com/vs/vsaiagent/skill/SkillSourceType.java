package com.vs.vsaiagent.skill;

/**
 * Skill 的来源类型：
 *  - LOCAL       本地 Java 实现
 *  - MCP         远端 MCP Server 暴露的工具
 *  - DIFY        Dify Workflow（Phase 2 引入）
 *  - REMOTE_HTTP 任意可调用的 HTTP 远端服务
 */
public enum SkillSourceType {
    LOCAL,
    MCP,
    DIFY,
    REMOTE_HTTP
}
