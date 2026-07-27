package com.vs.vsaiagent.observability.context;

public final class TraceContext {

    private static final ThreadLocal<TraceInfo> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(TraceInfo traceInfo) {
        HOLDER.set(traceInfo);
    }

    public static TraceInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Runnable wrap(Runnable task) {
        TraceInfo captured = HOLDER.get();
        return () -> {
            TraceInfo previous = HOLDER.get();
            try {
                if (captured == null) {
                    HOLDER.remove();
                } else {
                    HOLDER.set(captured);
                }
                task.run();
            } finally {
                if (previous == null) {
                    HOLDER.remove();
                } else {
                    HOLDER.set(previous);
                }
            }
        };
    }

    public static void setSessionId(String sessionId) {
        TraceInfo old = HOLDER.get();
        if (old == null) {
            return;
        }
        HOLDER.set(new TraceInfo(old.traceId(), old.requestId(), sessionId));
    }
}
