package com.vs.vsaiagent.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrationProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesTraceableEventAndEvidence() throws Exception {
        OrchestrationEvent event = OrchestrationEvent.of(
                "final_completed", "conversation-1", "request-1", "trace-1",
                Map.of("answer", "已完成"));
        Evidence evidence = Evidence.success(
                "tool", "weather", "天气为晴", Map.of("provider", "local"), "raw payload");

        String eventJson = objectMapper.writeValueAsString(event);
        String evidenceJson = objectMapper.writeValueAsString(evidence);

        assertTrue(eventJson.contains("conversation-1"));
        assertTrue(eventJson.contains("request-1"));
        assertTrue(eventJson.contains("trace-1"));
        assertTrue(evidenceJson.contains("天气为晴"));
        assertTrue(evidenceJson.contains("raw payload"));
    }

    @Test
    void rejectsUnknownRouteAndKeepsEvidenceSummaryIndependentFromPayload() {
        assertThrows(IllegalArgumentException.class, () -> RoutePlan.route("unknown"));

        Evidence evidence = Evidence.empty("tool", "web-search", "没有找到可用结果", Map.of());
        assertEquals("没有找到可用结果", evidence.summary());
        assertEquals("empty", evidence.status());
        assertEquals(List.of(), evidence.payload() instanceof List<?> list ? list : List.of());
    }
}
