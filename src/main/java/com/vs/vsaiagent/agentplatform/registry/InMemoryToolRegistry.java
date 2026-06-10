package com.vs.vsaiagent.agentplatform.registry;

import com.vs.vsaiagent.agentplatform.model.ToolMetadata;
import com.vs.vsaiagent.agentplatform.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryToolRegistry implements ToolRegistry {

    private final Map<String, AgentTool> toolMap = new ConcurrentHashMap<>();

    @Override
    public void register(AgentTool tool) {
        toolMap.put(tool.toolName().toLowerCase(Locale.ROOT), tool);
    }

    @Override
    public Optional<AgentTool> findByName(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolMap.get(toolName.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<AgentTool> searchByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return new ArrayList<>(toolMap.values());
        }
        return toolMap.values().stream()
                .filter(tool -> tool.metadata().getTags() != null && tool.metadata().getTags().contains(tag))
                .sorted(Comparator.comparing(AgentTool::toolName))
                .toList();
    }

    @Override
    public List<ToolMetadata> listMetadata() {
        return toolMap.values().stream()
                .map(AgentTool::metadata)
                .sorted(Comparator.comparing(ToolMetadata::getToolName))
                .toList();
    }
}
