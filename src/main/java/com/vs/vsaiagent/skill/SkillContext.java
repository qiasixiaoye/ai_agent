package com.vs.vsaiagent.skill;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Skill 执行上下文。通过 TraceContext 自动填充链路 ID，
 * 让 Skill 执行天然与 observability 模块打通。
 */
public final class SkillContext {

    private final String traceId;
    private final String requestId;
    private final String sessionId;
    private final Map<String, Object> attrs;

    private SkillContext(String traceId, String requestId, String sessionId, Map<String, Object> attrs) {
        this.traceId = traceId;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.attrs = attrs == null ? new HashMap<>() : new HashMap<>(attrs);
    }

    public String traceId() { return traceId; }
    public String requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    public Map<String, Object> attrs() { return Collections.unmodifiableMap(attrs); }

    public Object attr(String key) { return attrs.get(key); }

    public static Builder builder() {
        return new Builder();
    }

    public static SkillContext empty() {
        return builder().build();
    }

    public static final class Builder {
        private String traceId;
        private String requestId;
        private String sessionId;
        private final Map<String, Object> attrs = new HashMap<>();

        public Builder traceId(String v) { this.traceId = v; return this; }
        public Builder requestId(String v) { this.requestId = v; return this; }
        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder attr(String key, Object value) { this.attrs.put(key, value); return this; }
        public Builder attrs(Map<String, Object> v) {
            if (v != null) this.attrs.putAll(v);
            return this;
        }

        public SkillContext build() {
            return new SkillContext(traceId, requestId, sessionId, attrs);
        }
    }
}
