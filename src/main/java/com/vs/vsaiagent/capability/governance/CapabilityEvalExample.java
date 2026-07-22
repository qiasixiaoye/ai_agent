package com.vs.vsaiagent.capability.governance;

import java.util.Collections;
import java.util.List;

public record CapabilityEvalExample(
        String capabilityName,
        String capabilityType,
        boolean positive,
        String category,
        String input,
        String expectedOutcome,
        List<String> assertions
) {
    public CapabilityEvalExample {
        assertions = assertions == null ? Collections.emptyList() : List.copyOf(assertions);
    }
}

