package com.vs.vsaiagent.knowledgebase.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentUploadResponse {
    private String documentId;
    private String status;
    private Boolean duplicated;
    private String message;
}
