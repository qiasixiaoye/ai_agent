package com.vs.vsaiagent.orchestration;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-side, single-use confirmation gate for the demonstration export action. */
@Component
public class FieldResearchConfirmationService {

    private static final long TTL_MS = 5 * 60_000L;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public String issue(String conversationId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(token, new PendingConfirmation(conversationId, System.currentTimeMillis() + TTL_MS));
        return token;
    }

    public boolean consume(String conversationId, String token) {
        if (conversationId == null || conversationId.isBlank() || token == null || token.isBlank()) return false;
        AtomicBoolean accepted = new AtomicBoolean(false);
        pending.compute(token, (ignored, confirmation) -> {
            if (confirmation != null && confirmation.expiresAt() >= System.currentTimeMillis()
                    && conversationId.equals(confirmation.conversationId())) {
                accepted.set(true);
                return null;
            }
            return confirmation;
        });
        return accepted.get();
    }

    private record PendingConfirmation(String conversationId, long expiresAt) { }
}
