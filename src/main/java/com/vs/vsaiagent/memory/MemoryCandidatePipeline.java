package com.vs.vsaiagent.memory;

import com.vs.vsaiagent.orchestration.Evidence;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts durable memory only after a completed turn, never from stream fragments. */
@Component
public class MemoryCandidatePipeline {

    private static final Pattern PREFERENCE = Pattern.compile(
            "(?:我喜欢|我偏好|我习惯|我希望|我使用|i prefer|i like|i use)\\s*([^。.!！?？\\n]{1,120})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE = Pattern.compile(
            "(?:我叫|我的名字是|my name is|call me)\\s*([^，。,.!！?？\\n]{1,40})",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> SENSITIVE_HINTS = Set.of(
            "密码", "口令", "token", "secret", "身份证", "银行卡", "信用卡", "手机号", "邮箱", "password");

    private final HierarchicalChatMemory memory;

    public MemoryCandidatePipeline(HierarchicalChatMemory memory) {
        this.memory = memory;
    }

    public List<MemoryCandidate> onTurnCompleted(CompletedTurn turn) {
        if (turn == null || blank(turn.conversationId()) || blank(turn.userMessage())
                || blank(turn.finalAnswer()) || looksLikeRawExecution(turn.finalAnswer())) {
            return List.of();
        }
        MemoryCandidate candidate = extract(turn.userMessage());
        if (candidate == null || alreadyExists(turn.conversationId(), candidate)) {
            return List.of();
        }
        if ("pending".equals(candidate.status())) {
            return List.of(candidate);
        }
        Map<String, String> metadata = Map.of(
                "memoryKey", candidate.dedupeKey(),
                "source", "completed_turn",
                "candidateType", candidate.type(),
                "confidence", String.valueOf(candidate.confidence()));
        memory.rememberSemantic(turn.conversationId(), candidate.content(), candidate.importance(), metadata);
        return List.of(candidate);
    }

    public CompletableFuture<List<MemoryCandidate>> onTurnCompletedAsync(CompletedTurn turn) {
        return CompletableFuture.supplyAsync(() -> onTurnCompleted(turn));
    }

    private MemoryCandidate extract(String userMessage) {
        Matcher preference = PREFERENCE.matcher(userMessage.trim());
        Matcher profile = PROFILE.matcher(userMessage.trim());
        String type;
        String content;
        if (preference.find()) {
            type = "preference";
            content = preference.group().trim();
        } else if (profile.find()) {
            type = "profile_fact";
            content = profile.group().trim();
        } else {
            String normalizedMessage = userMessage.toLowerCase(Locale.ROOT);
            if (SENSITIVE_HINTS.stream().anyMatch(normalizedMessage::contains)) {
                return new MemoryCandidate("sensitive_fact", "检测到敏感信息，未保存原文",
                        0.2, 0.1, "sensitive", "sensitive:blocked", "pending");
            }
            return null;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        boolean sensitive = SENSITIVE_HINTS.stream().anyMatch(normalized::contains);
        String key = type + ":" + normalized.replaceAll("[^\\p{L}\\p{N}]", "");
        return new MemoryCandidate(type, content, sensitive ? 0.7 : 0.8,
                sensitive ? 0.55 : 0.92, sensitive ? "sensitive" : "normal", key,
                sensitive ? "pending" : "written");
    }

    private boolean alreadyExists(String conversationId, MemoryCandidate candidate) {
        return memory.snapshot(conversationId).semanticMemories().stream()
                .anyMatch(item -> Objects.equals(candidate.dedupeKey(), item.metadata().get("memoryKey")));
    }

    private boolean looksLikeRawExecution(String answer) {
        String normalized = answer.toLowerCase(Locale.ROOT);
        return normalized.contains("step 1:") || normalized.contains("工具:")
                || normalized.contains("no web results") || normalized.contains("rawtooloutput");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record CompletedTurn(String conversationId, String userMessage,
                                String finalAnswer, List<Evidence> evidence) {
        public CompletedTurn {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }
}
