package com.vs.vsaiagent.orchestration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRouterTest {

    private final CapabilityRouter router = new CapabilityRouter();
    private final CapabilityPolicy policy = new CapabilityPolicy();

    @Test
    void selectsRouteFromUserIntent() {
        assertEquals(RoutePlan.Route.DIRECT, router.route("你好，最近心情怎么样").route());
        assertEquals(RoutePlan.Route.KNOWLEDGE, router.route("根据项目资料说明接口边界").route());
        assertEquals(RoutePlan.Route.TOOL, router.route("查询今天的天气").route());
        assertEquals(RoutePlan.Route.MIXED, router.route("规划旅行并执行日历安排").route());
    }

    @Test
    void highRiskCapabilityRequiresConfirmation() {
        CapabilityCatalog catalog = new CapabilityCatalog(List.of(
                new CapabilityDescriptor("terminal", "tool", "HIGH", List.of("SYSTEM_COMMAND"), true)
        ));
        RoutePlan plan = new RoutePlan(RoutePlan.Route.TOOL, List.of("terminal"), false);

        PolicyDecision decision = policy.check(plan, catalog);

        assertTrue(decision.allowed());
        assertTrue(decision.requiresConfirmation());
    }

    @Test
    void unknownCapabilityIsBlocked() {
        PolicyDecision decision = policy.check(
                new RoutePlan(RoutePlan.Route.TOOL, List.of("missing"), false),
                new CapabilityCatalog(List.of()));

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("missing"));
    }
}
