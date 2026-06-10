package com.vs.vsaiagent.knowledgebase.entity;

import com.vs.vsaiagent.knowledgebase.enums.DocumentProcessStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDocumentEntity {
    private Long id;
    private String documentId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String contentHash;
    private String source;
    private String tags;
    private Integer version;
    private DocumentProcessStatus status;
    private String errorMessage;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
