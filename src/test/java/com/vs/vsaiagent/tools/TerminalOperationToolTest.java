package com.vs.vsaiagent.tools;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TerminalOperationToolTest {

    @Test
    void includesStandardErrorAndTheNonZeroExitCodeInTheResult() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        String result = terminalOperationTool.executeTerminalCommand("echo tool-error 1>&2 & exit /b 7");

        assertTrue(result.contains("tool-error"));
        assertTrue(result.contains("exit code: 7"));
    }

    @Test
    void rejectsDestructiveCommandsBeforeStartingAProcess() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();

        assertEquals("Command rejected by terminal safety policy.",
                terminalOperationTool.executeTerminalCommand("del never-created.txt"));
    }

    @Test
    void destroysCommandsThatExceedTheConfiguredTimeout() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(Duration.ofMillis(50));

        long startedAt = System.nanoTime();
        String result = terminalOperationTool.executeTerminalCommand("ping -n 3 127.0.0.1 > nul");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertEquals("Command timed out after 50 ms.", result);
        assertTrue(elapsedMs < 1_000, "timed-out child process should not outlive its parent shell");
    }
}
