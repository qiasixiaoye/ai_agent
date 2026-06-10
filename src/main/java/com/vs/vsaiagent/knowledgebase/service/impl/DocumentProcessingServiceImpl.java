package com.vs.vsaiagent.knowledgebase.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vs.vsaiagent.knowledgebase.entity.KnowledgeChunkEntity;
import com.vs.vsaiagent.knowledgebase.entity.KnowledgeDocumentEntity;
import com.vs.vsaiagent.knowledgebase.enums.DocumentProcessStatus;
import com.vs.vsaiagent.knowledgebase.repository.KnowledgeChunkRepository;
import com.vs.vsaiagent.knowledgebase.repository.KnowledgeDocumentRepository;
import com.vs.vsaiagent.knowledgebase.repository.KnowledgeVectorRepository;
import com.vs.vsaiagent.knowledgebase.service.DocumentParserService;
import com.vs.vsaiagent.knowledgebase.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeVectorRepository vectorRepository;
    private final DocumentParserService documentParserService;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private VectorStore pgVectorVectorStore;

    public DocumentProcessingServiceImpl(KnowledgeDocumentRepository documentRepository,
                                         KnowledgeChunkRepository chunkRepository,
                                         KnowledgeVectorRepository vectorRepository,
                                         DocumentParserService documentParserService,
                                         ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorRepository = vectorRepository;
        this.documentParserService = documentParserService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Async
    public void processAsync(KnowledgeDocumentEntity document, byte[] bytes) {
        String documentId = document.getDocumentId();
        documentRepository.updateStatus(documentId, DocumentProcessStatus.PROCESSING, null);
        try {
            if (pgVectorVectorStore == null) {
                throw new IllegalStateException("未找到 pgVectorVectorStore Bean");
            }
            String fullText = documentParserService.parseText(document.getFileName(), bytes);
            List<Document> splitDocuments = split(fullText, documentId, document.getFileName(), document.getFileType(), document.getSource(), document.getTags(), document.getVersion());

            vectorRepository.deleteByDocumentId(documentId);
            chunkRepository.deleteByDocumentId(documentId);

            List<KnowledgeChunkEntity> chunkEntities = new ArrayList<>();
            for (int i = 0; i < splitDocuments.size(); i++) {
                Document splitDoc = splitDocuments.get(i);
                chunkEntities.add(KnowledgeChunkEntity.builder()
                        .chunkId(UUID.randomUUID().toString())
                        .documentId(documentId)
                        .chunkIndex(i + 1)
                        .tokenCount(estimateToken(splitDoc.getText()))
                        .content(splitDoc.getText())
                        .metadataJson(toJson(splitDoc.getMetadata()))
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            chunkRepository.batchInsert(chunkEntities);
            pgVectorVectorStore.add(splitDocuments);
            documentRepository.markSuccess(documentId);
            log.info("document {} process success, chunks={}", documentId, splitDocuments.size());
        } catch (Exception e) {
            String err = e.getMessage();
            if (err != null && err.length() > 1000) {
                err = err.substring(0, 1000);
            }
            documentRepository.updateStatus(documentId, DocumentProcessStatus.FAILED, err);
            log.error("document {} process failed", documentId, e);
        }
    }

    @Override
    public void rebuildEmbeddingByDocumentId(String documentId) {
        if (pgVectorVectorStore == null) {
            throw new IllegalStateException("未找到 pgVectorVectorStore Bean");
        }
        List<KnowledgeChunkEntity> chunks = chunkRepository.listByDocumentId(documentId);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("未找到可重建的分块数据");
        }
        vectorRepository.deleteByDocumentId(documentId);
        List<Document> docs = new ArrayList<>();
        for (KnowledgeChunkEntity chunk : chunks) {
            Map<String, Object> metadata = fromJson(chunk.getMetadataJson());
            metadata.put("document_id", documentId);
            docs.add(new Document(chunk.getContent(), metadata));
        }
        documentRepository.updateStatus(documentId, DocumentProcessStatus.PROCESSING, null);
        pgVectorVectorStore.add(docs);
        documentRepository.markSuccess(documentId);
    }

    private List<Document> split(String text, String documentId, String fileName, String fileType, String source, String tags, Integer version) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("document_id", documentId);
        metadata.put("file_name", fileName);
        metadata.put("file_type", fileType);
        metadata.put("source", source);
        metadata.put("tags", tags);
        metadata.put("version", version);
        List<Document> original = List.of(new Document(text, metadata));
        TokenTextSplitter splitter = new TokenTextSplitter(500, 100, 10, 5000, true);
        List<Document> splitDocs = splitter.apply(original);
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < splitDocs.size(); i++) {
            Map<String, Object> splitMeta = new LinkedHashMap<>(splitDocs.get(i).getMetadata());
            splitMeta.put("chunk_index", i + 1);
            result.add(new Document(splitDocs.get(i).getText(), splitMeta));
        }
        return result;
    }

    private int estimateToken(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("metadata 序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String metadataJson) {
        try {
            if (metadataJson == null || metadataJson.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(metadataJson, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("metadata 反序列化失败", e);
        }
    }
}
