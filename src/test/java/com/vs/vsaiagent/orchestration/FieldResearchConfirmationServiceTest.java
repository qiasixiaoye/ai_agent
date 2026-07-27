package com.vs.vsaiagent.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldResearchConfirmationServiceTest {

    @Test
    void issuedConfirmationTokenCanBeConsumedOnceOnlyForSameConversation() {
        FieldResearchConfirmationService confirmations = new FieldResearchConfirmationService();
        String token = confirmations.issue("conversation-1");

        assertFalse(confirmations.consume("conversation-2", token));
        assertTrue(confirmations.consume("conversation-1", token));
        assertFalse(confirmations.consume("conversation-1", token));
    }
}
