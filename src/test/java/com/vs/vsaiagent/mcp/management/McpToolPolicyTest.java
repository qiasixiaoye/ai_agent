package com.vs.vsaiagent.mcp.management;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolPolicyTest {

    private final McpToolPolicy policy = new McpToolPolicy("", "terminal,shell,execute_command", "delete,write,send,deploy");

    @Test
    void blocksDeniedToolsAndRequiresConfirmationForHighRiskTools() {
        assertFalse(policy.evaluate("terminal_exec", true).allowed());
        assertFalse(policy.evaluate("delete_document", false).allowed());
        assertTrue(policy.evaluate("delete_document", false).confirmationRequired());
        assertTrue(policy.evaluate("delete_document", true).allowed());
        assertTrue(policy.evaluate("search_documents", false).allowed());
    }
}
