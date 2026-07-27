package com.vs.vsaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;

class VsManusTest {

    @Test
    void isNotDiscoveredAsASpringSingletonCandidate() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(true);

        boolean discovered = scanner.findCandidateComponents("com.vs.vsaiagent.agent").stream()
                .anyMatch(candidate -> VsManus.class.getName().equals(candidate.getBeanClassName()));

        assertFalse(discovered,
                "VsManus owns mutable execution state and must only be constructed per request");
    }
}
