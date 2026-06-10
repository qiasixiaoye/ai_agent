package com.vs.vsaiagent.knowledgebase.dto;

import lombok.Data;

@Data
public class DocumentUploadRequest {
    private String source;
    private String tags;
}
