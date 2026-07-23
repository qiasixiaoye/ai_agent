package com.vs.vsaiagent.orchestration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Restricted execution plan selected for one user request. */
public record RoutePlan(
        Route route,
        List<String> capabilityIds,
        boolean requiresConfirmation) {

    public RoutePlan {
        route = Objects.requireNonNull(route, "route");
        capabilityIds = capabilityIds == null ? List.of() : List.copyOf(capabilityIds);
    }

    public static RoutePlan route(String value) {
        if (value == null) {
            throw new IllegalArgumentException("route must not be null");
        }
        try {
            return new RoutePlan(Route.valueOf(value.trim().toUpperCase(Locale.ROOT)), List.of(), false);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported route: " + value, exception);
        }
    }

    public enum Route {
        DIRECT,
        KNOWLEDGE,
        TOOL,
        SKILL,
        MIXED
    }
}
