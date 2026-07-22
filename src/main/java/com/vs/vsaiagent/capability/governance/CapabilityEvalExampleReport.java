package com.vs.vsaiagent.capability.governance;

import java.util.Collections;
import java.util.List;

public record CapabilityEvalExampleReport(
        int total,
        int positiveCount,
        int negativeCount,
        List<CapabilityEvalExample> examples
) {
    public CapabilityEvalExampleReport {
        examples = examples == null ? Collections.emptyList() : List.copyOf(examples);
    }
}

