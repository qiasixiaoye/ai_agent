package com.vs.vsaiagent.eval.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.eval.CaseResult;
import com.vs.vsaiagent.eval.EvalCase;
import com.vs.vsaiagent.eval.EvalJudge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-Judge：用独立模型按 rubric 评分。
 *
 * 提示模型输出严格 JSON：{"pass": true|false, "reason": "..."}
 * 解析失败时降级为「未通过 + 解析失败」。
 *
 * 注意：被测模型与裁判模型最好异源/异 prompt，避免自夸偏差。
 * 当前简单复用同一 ChatModel；后续可注入独立 judgeChatModel。
 */
@Slf4j
@Component
public class LlmAsJudge implements EvalJudge {

    public static final String NAME = "llm_as_judge";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[^{}]*\"pass\"[^{}]*\\}", Pattern.DOTALL);

    private final ChatClient chatClient;

    public LlmAsJudge(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem("""
                        你是一个严格的评测裁判。给定题目、参考要点和模型答案，按 rubric 判断答案是否通过。
                        只输出严格 JSON，不要 markdown 标记，不要解释。
                        格式：{"pass": true|false, "reason": "简短判定理由"}
                        """)
                .build();
    }

    @Override
    public String name() { return NAME; }

    @Override
    public CaseResult judge(EvalCase evalCase, String actualOutput) {
        String prompt = buildPrompt(evalCase, actualOutput);
        boolean pass = false;
        String reason;
        try {
            String raw = chatClient.prompt().user(prompt).call().content();
            JsonNode node = parseJson(raw);
            if (node == null) {
                reason = "judge json parse failed: " + truncate(raw, 80);
            } else {
                pass = node.path("pass").asBoolean(false);
                reason = node.path("reason").asText("(no reason)");
            }
        } catch (Exception e) {
            reason = "judge invoke failed: " + e.getMessage();
            log.warn("[llm-judge] failed", e);
        }
        return new CaseResult(
                evalCase.id(),
                evalCase.input(),
                actualOutput,
                pass,
                reason,
                List.of(),
                0L,
                0L
        );
    }

    private static String buildPrompt(EvalCase c, String actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 题目\n").append(safe(c.input())).append("\n\n");
        if (c.rubric() != null && !c.rubric().isBlank()) {
            sb.append("# Rubric (通过标准)\n").append(c.rubric()).append("\n\n");
        }
        if (c.expectedContains() != null && !c.expectedContains().isEmpty()) {
            sb.append("# 参考要点 (期望覆盖)\n");
            for (String k : c.expectedContains()) sb.append("- ").append(k).append("\n");
            sb.append("\n");
        }
        sb.append("# 模型答案\n").append(safe(actual)).append("\n\n");
        sb.append("请按 rubric 给出判定。只输出 JSON。");
        return sb.toString();
    }

    private static JsonNode parseJson(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        try { return MAPPER.readTree(trimmed); } catch (Exception ignore) {}
        Matcher m = JSON_BLOCK.matcher(trimmed);
        if (m.find()) {
            try { return MAPPER.readTree(m.group()); } catch (Exception ignore) {}
        }
        return null;
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String truncate(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n) + "..." : s); }
}
