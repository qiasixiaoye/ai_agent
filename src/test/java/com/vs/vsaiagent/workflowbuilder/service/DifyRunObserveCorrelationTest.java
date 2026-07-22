package com.vs.vsaiagent.workflowbuilder.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L2 关联用到的两个纯解析辅助方法的单测（不依赖 Spring 上下文 / DB）：
 *  - extractToolName：从 agent_request_log.user_input（{@code tool=<name> args={...}}）抽工具名
 *  - extractCalledTool：从 agent_log 的 label（{@code CALL <tool>}）抽被调用工具名
 */
class DifyRunObserveCorrelationTest {

    @Test
    void extractToolNameParsesNameBeforeArgs() {
        assertThat(DifyRunObserveService.extractToolName("tool=astro_plan_summary args={\"a\":1}"))
                .isEqualTo("astro_plan_summary");
    }

    @Test
    void extractToolNameHandlesNoArgsSuffix() {
        assertThat(DifyRunObserveService.extractToolName("tool=cloud_cover")).isEqualTo("cloud_cover");
    }

    @Test
    void extractToolNameReturnsNullForNonToolInput() {
        assertThat(DifyRunObserveService.extractToolName(null)).isNull();
        assertThat(DifyRunObserveService.extractToolName("user_input here")).isNull();
        assertThat(DifyRunObserveService.extractToolName("tool= args={}")).isNull();
    }

    @Test
    void extractCalledToolParsesCallLabel() {
        assertThat(DifyRunObserveService.extractCalledTool("CALL milkyway_rise")).isEqualTo("milkyway_rise");
        assertThat(DifyRunObserveService.extractCalledTool("call exposure_advisor")).isEqualTo("exposure_advisor");
    }

    @Test
    void extractCalledToolReturnsNullForNonCallLabel() {
        assertThat(DifyRunObserveService.extractCalledTool("ROUND 1")).isNull();
        assertThat(DifyRunObserveService.extractCalledTool("deepseek-chat Thought")).isNull();
        assertThat(DifyRunObserveService.extractCalledTool(null)).isNull();
    }
}
