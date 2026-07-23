package com.vs.vsaiagent.orchestration;

/** Runtime policy gate. Routing can select a capability, but this gate authorizes execution. */
public class CapabilityPolicy {

    public PolicyDecision check(RoutePlan plan, CapabilityCatalog catalog) {
        if (plan == null || catalog == null) {
            return PolicyDecision.blocked("路由计划或能力目录为空");
        }
        boolean confirmation = plan.requiresConfirmation();
        for (String capabilityId : plan.capabilityIds()) {
            CapabilityDescriptor descriptor = catalog.find(capabilityId);
            if (descriptor == null) {
                return PolicyDecision.blocked("能力不存在: " + capabilityId);
            }
            confirmation = confirmation || descriptor.isHighRisk() || descriptor.requiresConfirmation();
        }
        return PolicyDecision.allowed(confirmation, confirmation ? "需要用户确认后执行" : "策略允许执行");
    }
}
