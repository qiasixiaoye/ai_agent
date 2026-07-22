package com.vs.vsaiagent.memory;

import com.vs.vsaiagent.observability.vo.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/memory/conversations")
public class MemoryController {

    private final HierarchicalChatMemory memory;
    private final ContextWindowManager contextWindowManager;

    public MemoryController(HierarchicalChatMemory memory, ContextWindowManager contextWindowManager) {
        this.memory = memory;
        this.contextWindowManager = contextWindowManager;
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationMemorySnapshot> snapshot(@PathVariable String conversationId) {
        return ApiResponse.success(memory.snapshot(conversationId));
    }

    @GetMapping("/{conversationId}/context")
    public ApiResponse<ContextAssembly> previewContext(@PathVariable String conversationId,
                                                        @RequestParam(defaultValue = "") String query,
                                                        @RequestParam(defaultValue = "2500") int tokenBudget) {
        return ApiResponse.success(contextWindowManager.assemble(conversationId, query, tokenBudget));
    }

    @PostMapping("/{conversationId}/semantic")
    public ApiResponse<ConversationMemorySnapshot> rememberSemantic(@PathVariable String conversationId,
                                                                     @RequestBody RememberRequest request) {
        memory.rememberSemantic(conversationId, request.content(), request.importance(), request.metadata());
        return ApiResponse.success(memory.snapshot(conversationId));
    }

    @PostMapping("/{conversationId}/episodes")
    public ApiResponse<ConversationMemorySnapshot> rememberEpisode(@PathVariable String conversationId,
                                                                    @RequestBody RememberRequest request) {
        memory.rememberEpisode(conversationId, request.content(), request.importance(), request.metadata());
        return ApiResponse.success(memory.snapshot(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Boolean> clear(@PathVariable String conversationId) {
        memory.clear(conversationId);
        return ApiResponse.success(true);
    }

    @DeleteMapping("/{conversationId}/items/{memoryId}")
    public ApiResponse<Boolean> forget(@PathVariable String conversationId,
                                       @PathVariable String memoryId) {
        return ApiResponse.success(memory.forget(conversationId, memoryId));
    }

    public record RememberRequest(String content, double importance, Map<String, String> metadata) {
        public RememberRequest {
            importance = Math.min(Math.max(importance, 0), 1);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
