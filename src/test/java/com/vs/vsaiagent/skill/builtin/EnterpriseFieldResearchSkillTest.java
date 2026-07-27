package com.vs.vsaiagent.skill.builtin;

import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import com.vs.vsaiagent.skill.SkillContext;
import com.vs.vsaiagent.skill.SkillResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseFieldResearchSkillTest {

    @Test
    void executesOnlyTheDeclaredReadOnlyWhitelistAndKeepsUnavailableEvidence() {
        RecordingToolService tools = new RecordingToolService();
        EnterpriseFieldResearchSkill skill = new EnterpriseFieldResearchSkill(tools);

        SkillResult result = skill.execute(Map.of("location", "北京朝阳区", "objective", "客户调研"),
                SkillContext.empty());

        assertTrue(result.success());
        assertEquals(List.of("field_weather_summary", "public_transit_risk", "field_research_brief"), tools.called);
        assertTrue(String.valueOf(result.data()).contains("unavailable"));
        assertFalse(String.valueOf(result.data()).contains("terminal_operation"));
    }

    private static final class RecordingToolService implements ToolExecutionService {
        private final List<String> called = new ArrayList<>();

        @Override public ToolExecuteResult executeByName(ToolExecuteRequest request) {
            called.add(request.getToolName());
            if ("public_transit_risk".equals(request.getToolName())) {
                return ToolExecuteResult.builder().toolName(request.getToolName()).success(false)
                        .errorMessage("unavailable: transit adapter unavailable").build();
            }
            return ToolExecuteResult.builder().toolName(request.getToolName()).success(true)
                    .output(request.getToolName() + " evidence").build();
        }
        @Override public ToolExecuteResult executeByMetadata(String tag, ToolExecuteRequest request) { return executeByName(request); }
        @Override public List<ToolMetadata> listTools() { return List.of(); }
    }
}
