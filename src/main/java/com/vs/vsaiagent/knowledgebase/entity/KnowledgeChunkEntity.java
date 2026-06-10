package com.vs.vsaiagent.knowledgebase.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeChunkEntity {
    private Long id;
    private String chunkId;
    private String documentId;
    private Integer chunkIndex;
    private Integer tokenCount;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;
}
