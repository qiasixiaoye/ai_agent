package com.vs.vsaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 终端操作工具
 */
public class TerminalOperationTool {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern DESTRUCTIVE_COMMAND = Pattern.compile(
            "(?i)(?:^|[\\s;&|()]+)(?:del|erase|rd|rmdir|format|shutdown|restart|diskpart|bcdedit|remove-item|rm)(?:\\s|$)");

    private final Duration timeout;

    public TerminalOperationTool() {
        this(DEFAULT_TIMEOUT);
    }

    TerminalOperationTool(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
    }

    @Tool(description = "Execute a non-destructive command in the terminal. Commands are time-limited and dangerous operations are blocked.")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        if (command == null || command.isBlank()) {
            return "Command must not be blank.";
        }
        if (DESTRUCTIVE_COMMAND.matcher(command).find()) {
            return "Command rejected by terminal safety policy.";
        }
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command)
                    .redirectErrorStream(true);
            Process process = builder.start();
            StringBuffer output = new StringBuffer();
            Thread outputReader = Thread.startVirtualThread(() -> readOutput(process, output));

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                destroyProcessTree(process);
                process.waitFor();
                outputReader.join();
                return "Command timed out after " + timeout.toMillis() + " ms.";
            }
            outputReader.join();
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command execution was interrupted.";
        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    private static void readOutput(Process process, StringBuffer output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (IOException e) {
            output.append("Error reading command output: ").append(e.getMessage());
        }
    }

    private static void destroyProcessTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
