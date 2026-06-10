package com.vs.vsaiagent.knowledgebase.service;

import com.vs.vsaiagent.knowledgebase.entity.KnowledgeDocumentEntity;

public interface DocumentProcessingService {
    void processAsync(KnowledgeDocumentEntity document, byte[] bytes);

    void rebuildEmbeddingByDocumentId(String documentId);
}
