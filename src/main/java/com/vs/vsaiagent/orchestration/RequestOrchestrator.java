package com.vs.vsaiagent.orchestration;

import com.vs.vsaiagent.app.AssistantApp;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Single entry point that routes a request and exposes typed progress events. */
@Component
public class RequestOrchestrator {

    private final AssistantApp assistantApp;
    private final CapabilityRouter capabilityRouter;

    public RequestOrchestrator(AssistantApp assistantApp, CapabilityRouter capabilityRouter) {
        this.assistantApp = assistantApp;
        this.capabilityRouter = capabilityRouter;
    }

    public Flux<ServerSentEvent<OrchestrationEvent>> stream(OrchestrationRequest request) {
        String conversationId = valueOr(request.conversationId(), UUID.randomUUID().toString());
        String requestId = valueOr(request.requestId(), UUID.randomUUID().toString());
        String traceId = valueOr(request.traceId(), UUID.randomUUID().toString());
        String message = request.message() == null ? "" : request.message().trim();

        if (message.isBlank()) {
            return Flux.just(event("request_failed", conversationId, requestId, traceId,
                    Map.of("message", "问题不能为空")));
        }

        RoutePlan plan = capabilityRouter.route(message);
        Flux<ServerSentEvent<OrchestrationEvent>> prefix = Flux.just(
                event("request_started", conversationId, requestId, traceId,
                        Map.of("messageLength", message.length())),
                event("route_selected", conversationId, requestId, traceId,
                        Map.of("route", plan.route().name(), "automatic", true)));
        Flux<String> answer = answerFor(plan.route(), message, conversationId);

        if (plan.route() == RoutePlan.Route.TOOL || plan.route() == RoutePlan.Route.SKILL
                || plan.route() == RoutePlan.Route.MIXED) {
            prefix = prefix.concatWithValues(event("capability_started", conversationId, requestId, traceId,
                    Map.of("route", plan.route().name())));
        }

        return prefix.concatWith(answer
                        .map(text -> event("final_delta", conversationId, requestId, traceId,
                                Map.of("text", text == null ? "" : text)))
                        .concatWith(Mono.fromSupplier(() -> event("final_completed", conversationId, requestId,
                                traceId, Map.of("status", "success"))))
                        .concatWith((plan.route() == RoutePlan.Route.TOOL || plan.route() == RoutePlan.Route.SKILL
                                || plan.route() == RoutePlan.Route.MIXED)
                                ? Mono.just(event("capability_completed", conversationId, requestId, traceId,
                                Map.of("status", "success")))
                                : Mono.empty())
                        .onErrorResume(error -> Flux.just(event("request_failed", conversationId, requestId, traceId,
                                Map.of("message", safeMessage(error))))));
    }

    private Flux<String> answerFor(RoutePlan.Route route, String message, String conversationId) {
        return switch (route) {
            case KNOWLEDGE -> assistantApp.doChatWithRagSse(message, conversationId);
            case TOOL, MIXED -> Mono.fromCallable(() -> assistantApp.doChatWithTools(message, conversationId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flux();
            case SKILL -> Mono.fromCallable(() -> assistantApp.doChatWithSkills(message, conversationId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flux();
            case DIRECT -> assistantApp.doChatByStream(message, conversationId);
        };
    }

    private ServerSentEvent<OrchestrationEvent> event(String type, String conversationId,
                                                       String requestId, String traceId,
                                                       Map<String, Object> payload) {
        Map<String, Object> safePayload = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        OrchestrationEvent event = OrchestrationEvent.of(type, conversationId, requestId, traceId, safePayload);
        return ServerSentEvent.<OrchestrationEvent>builder(event).event(type).build();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        return message == null || message.isBlank() ? "请求执行失败" : message;
    }
}
