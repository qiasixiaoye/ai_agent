package com.vs.vsaiagent.observability.vo;

import com.vs.vsaiagent.observability.entity.AgentRequestLogEntity;
import com.vs.vsaiagent.observability.entity.AgentStageLogEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RequestTraceVO {
    private AgentRequestLogEntity request;
    private List<AgentStageLogEntity> stages;
}
