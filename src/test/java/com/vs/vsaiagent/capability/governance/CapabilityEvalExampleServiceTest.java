package com.vs.vsaiagent.capability.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityEvalExampleServiceTest {

    @Test
    void generatesPositiveAndNegativeExamplesForHypotheticalCapabilities() {
        CapabilityEvalExampleReport report = new CapabilityEvalExampleService().generate();

        assertTrue(report.examples().stream().anyMatch(example ->
                example.capabilityName().equals("hypothetical-terminal-tool")
                        && !example.positive()
                        && example.expectedOutcome().contains("阻断")));
        assertTrue(report.examples().stream().anyMatch(example ->
                example.capabilityName().equals("hypothetical-weather-tool")
                        && example.positive()
                        && example.expectedOutcome().contains("结构化")));
        assertFalse(report.examples().stream()
                .filter(example -> example.capabilityName().startsWith("hypothetical-"))
                .toList()
                .isEmpty());
    }
}

