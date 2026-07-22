package com.vs.vsaiagent.context;

import org.springframework.stereotype.Component;

/**
 * Model-neutral token estimator. It is deliberately isolated so a model-specific
 * tokenizer can replace it without changing memory, skill or tool policies.
 */
@Component
public class TokenEstimator {

    public int estimate(String text) {
        if (text == null || text.isBlank()) return 0;
        int ascii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) <= 127) ascii++;
        }
        int nonAscii = text.length() - ascii;
        return (int) Math.ceil(ascii / 4.0 + nonAscii / 1.6);
    }

    public String fit(String text, int maxTokens) {
        if (text == null || text.isBlank() || maxTokens <= 0) return "";
        if (estimate(text) <= maxTokens) return text;
        String marker = "\n[truncated by token budget]";
        int markerTokens = estimate(marker);
        boolean includeMarker = maxTokens > markerTokens + 1;
        int contentBudget = includeMarker ? maxTokens - markerTokens : maxTokens;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (estimate(text.substring(0, mid)) <= contentBudget) low = mid;
            else high = mid - 1;
        }
        return text.substring(0, low).stripTrailing() + (includeMarker ? marker : "");
    }

    public String fitHeadTail(String text, int maxTokens) {
        if (text == null || text.isBlank() || maxTokens <= 0) return "";
        if (estimate(text) <= maxTokens) return text;
        int headBudget = Math.max(1, (int) (maxTokens * 0.72));
        int tailBudget = Math.max(1, maxTokens - headBudget - 16);
        String head = fit(text, headBudget).replace("\n[truncated by token budget]", "");
        String tail = fitTail(text, tailBudget);
        return head.stripTrailing() + "\n...[middle omitted by token budget]...\n" + tail.stripLeading();
    }

    private String fitTail(String text, int maxTokens) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int length = (low + high + 1) >>> 1;
            if (estimate(text.substring(text.length() - length)) <= maxTokens) low = length;
            else high = length - 1;
        }
        int start = text.length() - low;
        if (start > 0 && start < text.length()
                && Character.isLowSurrogate(text.charAt(start))) start++;
        return text.substring(Math.min(start, text.length()));
    }
}
