package com.vs.vsaiagent.mcp.management;

import com.vs.vsaiagent.memory.HierarchicalChatMemory;
import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import com.vs.vsaiagent.observability.service.ExecutionLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ManagedMcpToolServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanTrace() {
        TraceContext.clear();
    }

    @Test
    void discoversGovernedToolsAndWritesSuccessfulEpisode() {
        ManagedMcpToolService service = service(callback("search_docs", false), callback("delete_docs", false));
        TraceContext.set(new TraceInfo("trace", "request", "session-1"));

        assertEquals(2, service.refreshCatalog().size());
        assertEquals(1, service.allowedToolCallbacks().length);
        assertFalse(service.invoke("delete_docs", "{}", false).success());
        assertTrue(service.invoke("search_docs", "{\"query\":\"java\"}", false).success());
        assertEquals(1, serviceMemory(service).snapshot("session-1").episodicMemories().size());
        service.destroy();
    }

    @Test
    void opensCircuitAfterConsecutiveFailures() {
        ManagedMcpToolService service = service(callback("unstable_lookup", true));
        service.refreshCatalog();

        assertFalse(service.invoke("unstable_lookup", "{}", false).success());
        McpToolCallResult second = service.invoke("unstable_lookup", "{}", false);
        assertEquals("OPEN", second.circuitState());
        assertEquals("OPEN", service.invoke("unstable_lookup", "{}", false).circuitState());
        service.destroy();
    }

    private ManagedMcpToolService service(ToolCallback... callbacks) {
        HierarchicalChatMemory memory = new HierarchicalChatMemory(tempDir.toString(), 6, 1000, 20);
        ManagedMcpToolService service = new ManagedMcpToolService(
                new McpToolPolicy("", "terminal,shell", "delete,write,send"),
                mock(ExecutionLogService.class), memory, 30_000, 2_000, 2, 60_000);
        ReflectionTestUtils.setField(service, "provider", ToolCallbackProvider.from(callbacks));
        return service;
    }

    private HierarchicalChatMemory serviceMemory(ManagedMcpToolService service) {
        return (HierarchicalChatMemory) ReflectionTestUtils.getField(service, "memory");
    }

    private ToolCallback callback(String name, boolean fail) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(name)
                        .inputSchema("{\"type\":\"object\"}").build();
            }

            @Override
            public String call(String toolInput) {
                if (fail) throw new IllegalStateException("boom");
                return "ok:" + toolInput;
            }
        };
    }
}
