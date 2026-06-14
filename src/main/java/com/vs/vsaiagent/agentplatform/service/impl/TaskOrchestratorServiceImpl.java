package com.vs.vsaiagent.agentplatform.service.impl;

import com.vs.vsaiagent.agentplatform.model.TaskExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.TaskExecuteResult;
import com.vs.vsaiagent.agentplatform.model.TaskExecutionContext;
import com.vs.vsaiagent.agentplatform.model.TaskStepDefinition;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteRequest;
import com.vs.vsaiagent.agentplatform.model.ToolExecuteResult;
import com.vs.vsaiagent.agentplatform.service.TaskOrchestratorService;
import com.vs.vsaiagent.agentplatform.service.ToolExecutionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskOrchestratorServiceImpl implements TaskOrchestratorService {

    private final ToolExecutionService toolExecutionService;

    public TaskOrchestratorServiceImpl(ToolExecutionService toolExecutionService) {
        this.toolExecutionService = toolExecutionService;
    }

    @Override
    public TaskExecuteResult execute(TaskExecuteRequest request) {
        String traceId = request.getTraceId() == null ? UUID.randomUUID().toString() : request.getTraceId();
        int maxSteps = request.getMaxSteps() == null ? 6 : Math.min(request.getMaxSteps(), 20);
        List<TaskStepDefinition> steps = request.getSteps() == null ? List.of() : request.getSteps();
        TaskExecutionContext context = TaskExecutionContext.create(traceId, maxSteps);
        List<ToolExecuteResult> results = new ArrayList<>();
        boolean success = true;
        for (TaskStepDefinition step : steps) {
            if (context.getCurrentStep() >= context.getMaxSteps()) {
                success = false;
                results.add(ToolExecuteResult.builder()
                        .toolName(step.getToolName())
                        .success(false)
                        .errorMessage("超过最大执行步数限制")
                        .build());
                break;
            }
            context.setCurrentStep(context.getCurrentStep() + 1);
            Map<String, Object> resolvedArgs = resolveArgs(step.getArgs(), context);
            ToolExecuteResult result = toolExecutionService.executeByName(ToolExecuteRequest.builder()
                    .traceId(traceId)
                    .toolName(step.getToolName())
                    .arguments(resolvedArgs)
                    .build());
            results.add(result);
            context.getStepResults().put(step.getStepId(), result);
            if (!result.isSuccess() && step.isRequired()) {
                success = false;
                break;
            }
        }
        String summary = results.isEmpty() ? "" : results.get(results.size() - 1).getOutput();
        return TaskExecuteResult.builder()
                .traceId(traceId)
                .success(success)
                .executedSteps(context.getCurrentStep())
                .summary(summary)
                .results(results)
                .build();
    }

    @Override
    public TaskExecuteResult runDemoFlow(String query) {
        List<TaskStepDefinition> steps = List.of(
                TaskStepDefinition.builder()
                        .stepId("s1")
                        .toolName("web_search")
                        .args(Map.of("query", query))
                        .required(true)
                        .build(),
                TaskStepDefinition.builder()
                        .stepId("s2")
                        .toolName("image_search")
                        .args(Map.of("query", query))
                        .required(false)
                        .build(),
                TaskStepDefinition.builder()
                        .stepId("s3")
                        .toolName("result_summary")
                        .args(Map.of(
                                "searchResult", "${step:s1}",
                                "imageResult", "${step:s2}"
                        ))
                        .required(true)
                        .build()
        );
        TaskExecuteRequest request = new TaskExecuteRequest();
        request.setTraceId(UUID.randomUUID().toString());
        request.setMaxSteps(6);
        request.setSteps(steps);
        return execute(request);
    }

    @Override
    public TaskExecuteResult runAstroPhotoFlow(double latitude, double longitude, String date) {
        List<TaskStepDefinition> steps = List.of(
                TaskStepDefinition.builder()
                        .stepId("s1")
                        .toolName("milkyway_rise")
                        .args(Map.of("latitude", latitude, "longitude", longitude, "date", date))
                        .required(true)
                        .build(),
                TaskStepDefinition.builder()
                        .stepId("s2")
                        .toolName("light_pollution")
                        .args(Map.of("latitude", latitude, "longitude", longitude))
                        .required(true)
                        .build(),
                TaskStepDefinition.builder()
                        .stepId("s3")
                        .toolName("cloud_cover")
                        .args(Map.of("latitude", latitude, "longitude", longitude, "date", date))
                        .required(false)
                        .build(),
                TaskStepDefinition.builder()
                        .stepId("s4")
                        .toolName("astro_plan_summary")
                        .args(Map.of(
                                "milkyWayResult", "${step:s1}",
                                "lightPollutionResult", "${step:s2}",
                                "cloudCoverResult", "${step:s3}",
                                "location", latitude + "," + longitude,
                                "date", date
                        ))
                        .required(true)
                        .build()
        );
        TaskExecuteRequest request = new TaskExecuteRequest();
        request.setTraceId(UUID.randomUUID().toString());
        request.setMaxSteps(6);
        request.setSteps(steps);
        return execute(request);
    }

    private Map<String, Object> resolveArgs(Map<String, Object> rawArgs, TaskExecutionContext context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (rawArgs == null) {
            return resolved;
        }
        for (Map.Entry<String, Object> entry : rawArgs.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str && str.startsWith("${step:") && str.endsWith("}")) {
                String stepId = str.substring(7, str.length() - 1);
                ToolExecuteResult prev = context.getStepResults().get(stepId);
                resolved.put(entry.getKey(), prev == null ? "" : prev.getOutput());
                continue;
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }
}
