package com.vs.vsaiagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.context.TokenEstimator;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件持久化的分层会话记忆。
 *
 * <p>工作记忆保留最近消息；被挤出的消息合并到滚动摘要；用户明确表达的稳定事实进入
 * 语义记忆；工具调用和任务结果进入情景记忆。每个会话独立加锁并通过临时文件原子替换，
 * 避免原 FileBasedChatMemory 的并发覆盖和 conversationId 路径穿越问题。</p>
 */
@Component
public class HierarchicalChatMemory implements ChatMemory {

    private static final Pattern[] FACT_PATTERNS = new Pattern[] {
            Pattern.compile("(?:我叫|我的名字是|请叫我)([^，。；\\n]{1,40})"),
            Pattern.compile("(?:我喜欢|我偏好|我习惯|我希望|我使用)([^。；\\n]{2,120})"),
            Pattern.compile("(?i)(?:my name is|call me|i prefer|i use)\\s+([^.;\\n]{2,120})")
    };

    private final Path baseDir;
    private final int workingLimit;
    private final int summaryMaxChars;
    private final int tierItemLimit;
    private final int historyTokenBudget;
    private final TokenEstimator tokenEstimator = new TokenEstimator();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public HierarchicalChatMemory(
            @Value("${app.memory.base-dir:${user.dir}/tmp/agent-memory}") String baseDir,
            @Value("${app.memory.working-message-limit:20}") int workingLimit,
            @Value("${app.memory.summary-max-chars:4000}") int summaryMaxChars,
            @Value("${app.memory.tier-item-limit:100}") int tierItemLimit,
            @Value("${app.context.history-token-budget:5000}") int historyTokenBudget) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        this.workingLimit = Math.max(4, workingLimit);
        this.summaryMaxChars = Math.max(500, summaryMaxChars);
        this.tierItemLimit = Math.max(10, tierItemLimit);
        this.historyTokenBudget = Math.max(256, historyTokenBudget);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("create memory directory failed: " + this.baseDir, e);
        }
    }

    public HierarchicalChatMemory(String baseDir, int workingLimit, int summaryMaxChars, int tierItemLimit) {
        this(baseDir, workingLimit, summaryMaxChars, tierItemLimit, 5000);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) return;
        withState(conversationId, state -> {
            long now = System.currentTimeMillis();
            for (Message message : messages) {
                StoredMessage stored = new StoredMessage(message.getMessageType().getValue(), message.getText(), now);
                state.messages.add(stored);
                if (message.getMessageType() == MessageType.USER) extractFacts(state, message.getText(), now);
            }
            compactWorkingMemory(state);
            state.updatedAt = now;
            return null;
        }, true);
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        return withState(conversationId, state -> {
            int from = Math.max(0, state.messages.size() - Math.max(lastN, 0));
            List<StoredMessage> candidates = state.messages.subList(from, state.messages.size());
            List<StoredMessage> selected = new ArrayList<>();
            int used = 0;
            for (int i = candidates.size() - 1; i >= 0; i--) {
                StoredMessage message = candidates.get(i);
                int tokens = tokenEstimator.estimate(message.text()) + 4;
                if (used + tokens > historyTokenBudget) {
                    if (selected.isEmpty()) {
                        selected.add(new StoredMessage(message.role(),
                                tokenEstimator.fit(message.text(), historyTokenBudget - 4), message.createdAt()));
                    }
                    break;
                }
                selected.add(message);
                used += tokens;
            }
            java.util.Collections.reverse(selected);
            return selected.stream().map(this::toMessage).toList();
        }, false);
    }

    @Override
    public void clear(String conversationId) {
        ReentrantLock lock = lock(conversationId);
        lock.lock();
        try {
            Files.deleteIfExists(fileFor(conversationId));
        } catch (IOException e) {
            throw new IllegalStateException("clear conversation memory failed", e);
        } finally {
            lock.unlock();
        }
    }

    public void rememberSemantic(String conversationId, String content, double importance, Map<String, String> metadata) {
        remember(conversationId, MemoryTier.SEMANTIC, content, importance, metadata);
    }

    public void rememberEpisode(String conversationId, String content, double importance, Map<String, String> metadata) {
        remember(conversationId, MemoryTier.EPISODIC, content, importance, metadata);
    }

    public boolean forget(String conversationId, String memoryId) {
        if (memoryId == null || memoryId.isBlank()) return false;
        return withState(conversationId, state ->
                state.semantic.removeIf(item -> memoryId.equals(item.id()))
                        | state.episodes.removeIf(item -> memoryId.equals(item.id())), true);
    }

    public List<MemoryItem> search(String conversationId, MemoryTier tier, String query, int limit) {
        return searchDetailed(conversationId, tier, query, limit).stream().map(MemoryRecall::item).toList();
    }

    public List<MemoryRecall> searchDetailed(String conversationId, MemoryTier tier, String query, int limit) {
        return withState(conversationId, state -> {
            List<MemoryItem> source = tier == MemoryTier.EPISODIC ? state.episodes : state.semantic;
            long now = System.currentTimeMillis();
            boolean blankQuery = query == null || query.isBlank();
            List<MemoryRecall> scored = source.stream()
                    .map(item -> scoreRecall(item, query, now, blankQuery))
                    .sorted(Comparator.comparingDouble(MemoryRecall::score).reversed()
                            .thenComparingLong(item -> -item.item().createdAt()))
                    .toList();
            List<MemoryRecall> selected = scored.stream()
                    .filter(item -> blankQuery || item.relevance() > 0)
                    .limit(Math.max(0, limit))
                    .toList();
            if (selected.isEmpty() && tier == MemoryTier.EPISODIC && limit > 0 && !scored.isEmpty()) {
                MemoryRecall fallback = scored.getFirst();
                List<String> reasons = new ArrayList<>(fallback.reasons());
                reasons.add("continuityFallback=true");
                selected = List.of(new MemoryRecall(fallback.item(), fallback.score(),
                        fallback.relevance(), reasons));
            }
            Map<String, MemoryRecall> selectedById = selected.stream()
                    .collect(java.util.stream.Collectors.toMap(r -> r.item().id(), r -> r));
            for (int i = 0; i < source.size(); i++) {
                MemoryRecall recall = selectedById.get(source.get(i).id());
                if (recall == null) continue;
                MemoryItem old = source.get(i);
                MemoryItem updated = new MemoryItem(old.id(), old.tier(), old.content(), old.importance(),
                        old.createdAt(), now, old.accessCount() + 1, old.metadata());
                source.set(i, updated);
                selectedById.put(old.id(), new MemoryRecall(updated, recall.score(),
                        recall.relevance(), recall.reasons()));
            }
            return selected.stream().map(r -> selectedById.get(r.item().id())).toList();
        }, true);
    }

    public ConversationMemorySnapshot snapshot(String conversationId) {
        return withState(conversationId, state -> new ConversationMemorySnapshot(
                conversationId, state.messages.size(), state.summary,
                List.copyOf(state.semantic), List.copyOf(state.episodes), state.updatedAt), false);
    }

    public HistoryUsage historyUsage(String conversationId, int lastN) {
        ConversationMemorySnapshot snapshot = snapshot(conversationId);
        List<Message> selected = get(conversationId, lastN);
        int tokens = selected.stream().mapToInt(message -> tokenEstimator.estimate(message.getText()) + 4).sum();
        int requested = Math.min(Math.max(lastN, 0), snapshot.workingMessageCount());
        return new HistoryUsage(snapshot.workingMessageCount(), selected.size(), tokens,
                historyTokenBudget, selected.size() < requested);
    }

    private void remember(String conversationId, MemoryTier tier, String content,
                          double importance, Map<String, String> metadata) {
        if (content == null || content.isBlank()) return;
        withState(conversationId, state -> {
            List<MemoryItem> target = tier == MemoryTier.EPISODIC ? state.episodes : state.semantic;
            String normalized = normalize(content);
            String memoryKey = metadata == null ? null : metadata.get("memoryKey");
            if (memoryKey != null && !memoryKey.isBlank()) {
                for (int i = 0; i < target.size(); i++) {
                    MemoryItem old = target.get(i);
                    if (memoryKey.equals(old.metadata().get("memoryKey"))) {
                        long now = System.currentTimeMillis();
                        target.set(i, new MemoryItem(old.id(), tier, content.trim(),
                                Math.min(Math.max(importance, 0), 1), old.createdAt(), now,
                                old.accessCount(), metadata));
                        state.updatedAt = now;
                        return null;
                    }
                }
            }
            boolean duplicate = target.stream().anyMatch(item -> normalize(item.content()).equals(normalized));
            if (!duplicate) {
                long now = System.currentTimeMillis();
                target.add(new MemoryItem(UUID.randomUUID().toString(), tier, content.trim(),
                        Math.min(Math.max(importance, 0), 1), now, now, 0, metadata));
                trimTier(target, now);
                state.updatedAt = now;
            }
            return null;
        }, true);
    }

    private void extractFacts(ConversationState state, String text, long now) {
        if (text == null || text.isBlank()) return;
        for (Pattern pattern : FACT_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String fact = matcher.group().trim();
                if (state.semantic.stream().noneMatch(item -> normalize(item.content()).equals(normalize(fact)))) {
                    state.semantic.add(new MemoryItem(UUID.randomUUID().toString(), MemoryTier.SEMANTIC,
                            fact, 0.8, now, now, 0, Map.of("source", "conversation", "extractor", "rule")));
                }
            }
        }
        trimTier(state.semantic, now);
    }

    private void compactWorkingMemory(ConversationState state) {
        int totalTokens = state.messages.stream()
                .mapToInt(message -> tokenEstimator.estimate(message.text()) + 4).sum();
        if (state.messages.size() <= workingLimit && totalTokens <= historyTokenBudget) return;
        int evictCount = Math.max(0, state.messages.size() - workingLimit);
        int retainedTokens = totalTokens;
        for (int i = 0; i < evictCount; i++) {
            retainedTokens -= tokenEstimator.estimate(state.messages.get(i).text()) + 4;
        }
        while (retainedTokens > historyTokenBudget
                && state.messages.size() - evictCount > 4) {
            StoredMessage message = state.messages.get(evictCount++);
            retainedTokens -= tokenEstimator.estimate(message.text()) + 4;
        }
        // Avoid retaining an assistant message without the user message that led to it.
        while (evictCount < state.messages.size()
                && "assistant".equals(state.messages.get(evictCount).role())) {
            evictCount++;
        }
        List<StoredMessage> evicted = new ArrayList<>(state.messages.subList(0, evictCount));
        state.messages.subList(0, evictCount).clear();
        StringBuilder summary = new StringBuilder(state.summary == null ? "" : state.summary);
        if (summary.isEmpty()) summary.append("[conversation checkpoint]");
        for (StoredMessage message : evicted) {
            if (!summary.isEmpty()) summary.append('\n');
            summary.append(roleLabel(message.role())).append(": ").append(truncate(message.text(), 240));
        }
        if (summary.length() > summaryMaxChars) {
            summary.delete(0, summary.length() - summaryMaxChars);
            int firstBreak = summary.indexOf("\n");
            if (firstBreak >= 0) summary.delete(0, firstBreak + 1);
        }
        state.summary = summary.toString();
    }

    private Message toMessage(StoredMessage message) {
        return switch (message.role()) {
            case "assistant" -> new AssistantMessage(message.text());
            case "system" -> new SystemMessage(message.text());
            default -> new UserMessage(message.text());
        };
    }

    private <T> T withState(String conversationId, StateFunction<T> function, boolean save) {
        String useId = requireConversationId(conversationId);
        ReentrantLock lock = lock(useId);
        lock.lock();
        try {
            ConversationState state = read(useId);
            T result = function.apply(state);
            if (save) write(useId, state);
            return result;
        } finally {
            lock.unlock();
        }
    }

    private ConversationState read(String conversationId) {
        Path file = fileFor(conversationId);
        if (!Files.exists(file)) return new ConversationState();
        try {
            ConversationState state = mapper.readValue(file.toFile(), ConversationState.class);
            state.normalize();
            return state;
        } catch (IOException e) {
            throw new IllegalStateException("read conversation memory failed: " + conversationId, e);
        }
    }

    private void write(String conversationId, ConversationState state) {
        Path target = fileFor(conversationId);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), state);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignore) { }
            throw new IllegalStateException("persist conversation memory failed: " + conversationId, e);
        }
    }

    private Path fileFor(String conversationId) {
        return baseDir.resolve(sha256(requireConversationId(conversationId)) + ".json").normalize();
    }

    private ReentrantLock lock(String conversationId) {
        return locks.computeIfAbsent(requireConversationId(conversationId), ignored -> new ReentrantLock());
    }

    private static String requireConversationId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("conversationId must not be blank");
        return value;
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static double relevance(String query, String content) {
        String q = normalize(query);
        String c = normalize(content);
        if (q.isBlank() || c.isBlank()) return 0;
        if (q.contains(c) || c.contains(q)) return 1;
        int hits = 0;
        for (int i = 0; i < q.length() - 1; i++) if (c.contains(q.substring(i, i + 2))) hits++;
        return q.length() < 2 ? 0 : (double) hits / (q.length() - 1);
    }

    private MemoryRecall scoreRecall(MemoryItem item, String query, long now, boolean blankQuery) {
        double lexical = blankQuery ? 0.25 : relevance(query, item.content());
        double ageDays = Math.max(0, now - item.createdAt()) / 86_400_000.0;
        double recency = 1.0 / (1.0 + ageDays / 30.0);
        double frequency = Math.min(1.0, Math.log1p(item.accessCount()) / Math.log(10));
        double score = lexical * 0.65 + item.importance() * 0.20 + recency * 0.10 + frequency * 0.05;
        List<String> reasons = List.of(
                "relevance=" + round(lexical),
                "importance=" + round(item.importance()),
                "recency=" + round(recency),
                "frequency=" + round(frequency));
        return new MemoryRecall(item, round(score), round(lexical), reasons);
    }

    private void trimTier(List<MemoryItem> items, long now) {
        while (items.size() > tierItemLimit) {
            MemoryItem victim = items.stream().min(Comparator
                    .comparingDouble((MemoryItem item) -> retentionScore(item, now))
                    .thenComparingLong(MemoryItem::createdAt)).orElse(items.getFirst());
            items.remove(victim);
        }
    }

    private double retentionScore(MemoryItem item, long now) {
        double ageDays = Math.max(0, now - item.createdAt()) / 86_400_000.0;
        double recency = 1.0 / (1.0 + ageDays / 30.0);
        double frequency = Math.min(1.0, Math.log1p(item.accessCount()) / Math.log(10));
        return item.importance() * 0.65 + recency * 0.25 + frequency * 0.10;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String roleLabel(String role) {
        return "assistant".equals(role) ? "助手" : "system".equals(role) ? "系统" : "用户";
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private interface StateFunction<T> {
        T apply(ConversationState state);
    }

    public record StoredMessage(String role, String text, long createdAt) {
    }

    public static class ConversationState {
        public List<StoredMessage> messages = new ArrayList<>();
        public String summary = "";
        public List<MemoryItem> semantic = new ArrayList<>();
        public List<MemoryItem> episodes = new ArrayList<>();
        public long updatedAt = System.currentTimeMillis();

        public ConversationState() {
        }

        void normalize() {
            if (messages == null) messages = new ArrayList<>();
            if (summary == null) summary = "";
            if (semantic == null) semantic = new ArrayList<>();
            if (episodes == null) episodes = new ArrayList<>();
        }
    }
}
