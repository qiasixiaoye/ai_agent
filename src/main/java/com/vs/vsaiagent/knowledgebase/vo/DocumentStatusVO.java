package com.vs.vsaiagent.knowledgebase.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentStatusVO {
    private String documentId;
    private String fileName;
    private String fileType;
    private String source;
    private String tags;
    private Integer version;
    private String status;
    private String errorMessage;
    private Integer chunkCount;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;
}
