package com.vs.vsaiagent.observability.dto;

import lombok.Data;

@Data
public class FailQueryDTO {
    private String startTime;
    private String endTime;
    private Integer limit;
}
