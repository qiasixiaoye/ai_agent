package com.vs.vsaiagent.observability.config;

import cn.hutool.core.util.StrUtil;
import com.vs.vsaiagent.observability.context.TraceContext;
import com.vs.vsaiagent.observability.context.TraceInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceContextFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String traceId = defaultId(request.getHeader(HEADER_TRACE_ID));
        String requestId = defaultId(request.getHeader(HEADER_REQUEST_ID));
        String sessionId = request.getHeader(HEADER_SESSION_ID);
        if (StrUtil.isBlank(sessionId)) {
            sessionId = request.getParameter("chatId");
        }
        if (StrUtil.isBlank(sessionId)) {
            sessionId = "default";
        }
        TraceContext.set(new TraceInfo(traceId, requestId, sessionId));
        response.setHeader(HEADER_TRACE_ID, traceId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        response.setHeader(HEADER_SESSION_ID, sessionId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    private String defaultId(String value) {
        return StrUtil.isBlank(value) ? UUID.randomUUID().toString() : value;
    }
}
