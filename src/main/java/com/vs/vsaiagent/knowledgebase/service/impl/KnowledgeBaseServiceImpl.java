package com.vs.vsaiagent.knowledgebase.service.impl;

import cn.hutool.core.util.StrUtil;
import com.vs.vsaiagent.knowledgebase.entity.KnowledgeDocumentEntity;
import com.vs.vsaiagent.knowledgebase.enums.DocumentProcessStatus;
import com.vs.vsaiagent.knowledgebase.repository.KnowledgeChunkRepository;
import com.vs.vsaiagent.knowledgebase.repository.KnowledgeDocumentRepository;
import com.vs.vsaiagent.knowledgebase.repository.KnowledgeVectorRepository;
import com.vs.vsaiagent.knowledgebase.service.DocumentProcessingService;
import com.vs.vsaiagent.knowledgebase.service.KnowledgeBaseService;
import com.vs.vsaiagent.knowledgebase.util.FileHashUtils;
import com.vs.vsaiagent.knowledgebase.vo.DocumentStatusVO;
import com.vs.vsaiagent.knowledgebase.vo.DocumentUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeVectorRepository vectorRepository;
    private final DocumentProcessingService documentProcessingService;

    public KnowledgeBaseServiceImpl(KnowledgeDocumentRepository documentRepository,
                                    KnowledgeChunkRepository chunkRepository,
                                    KnowledgeVectorRepository vectorRepository,
                                    DocumentProcessingService documentProcessingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorRepository = vectorRepository;
        this.documentProcessingService = documentProcessingService;
    }

    @Override
    public DocumentUploadResponse upload(MultipartFile file, String source, String tags) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        String fileType = extension(fileName);
        validateFileType(fileType);
        byte[] bytes = getBytes(file);
        String contentHash = FileHashUtils.sha256Hex(bytes);
        KnowledgeDocumentEntity duplicated = documentRepository.findByContentHash(contentHash).orElse(null);
        if (duplicated != null && duplicated.getStatus() == DocumentProcessStatus.SUCCESS) {
            return DocumentUploadResponse.builder()
                    .documentId(duplicated.getDocumentId())
                    .status(duplicated.getStatus().name())
                    .duplicated(true)
                    .message("重复文档，直接复用已有知识库")
                    .build();
        }
        String docId = UUID.randomUUID().toString();
        String docSource = StrUtil.isBlank(source) ? "manual-upload" : source;
        int version = documentRepository.nextVersion(docSource, fileName);
        KnowledgeDocumentEntity entity = KnowledgeDocumentEntity.builder()
                .documentId(docId)
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(file.getSize())
                .contentHash(contentHash)
                .source(docSource)
                .tags(tags)
                .version(version)
                .status(DocumentProcessStatus.PENDING)
                .uploadedAt(LocalDateTime.now())
                .build();
        documentRepository.save(entity);
        documentProcessingService.processAsync(entity, bytes);
        return DocumentUploadResponse.builder()
                .documentId(docId)
                .status(DocumentProcessStatus.PENDING.name())
                .duplicated(false)
                .message("文档已上传，开始异步处理")
                .build();
    }

    @Override
    public DocumentStatusVO getStatus(String documentId) {
        KnowledgeDocumentEntity doc = documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("documentId 不存在"));
        return toVO(doc);
    }

    @Override
    public List<DocumentStatusVO> listRecent(int limit) {
        int size = limit <= 0 ? 20 : Math.min(limit, 200);
        return documentRepository.listRecent(size).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void deleteDocument(String documentId) {
        documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("documentId 不存在"));
        vectorRepository.deleteByDocumentId(documentId);
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.updateStatus(documentId, DocumentProcessStatus.DELETED, null);
    }

    @Override
    public void reprocessDocument(String documentId) {
        KnowledgeDocumentEntity doc = documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("documentId 不存在"));
        if (doc.getStatus() == DocumentProcessStatus.PROCESSING) {
            throw new IllegalStateException("文档处理中，暂不支持重建");
        }
        documentProcessingService.rebuildEmbeddingByDocumentId(documentId);
    }

    @Override
    public void rebuildVectorIndex() {
        vectorRepository.rebuildIndex();
    }

    private DocumentStatusVO toVO(KnowledgeDocumentEntity doc) {
        return DocumentStatusVO.builder()
                .documentId(doc.getDocumentId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .source(doc.getSource())
                .tags(doc.getTags())
                .version(doc.getVersion())
                .status(doc.getStatus().name())
                .errorMessage(doc.getErrorMessage())
                .chunkCount(chunkRepository.countByDocumentId(doc.getDocumentId()))
                .uploadedAt(doc.getUploadedAt())
                .processedAt(doc.getProcessedAt())
                .build();
    }

    private void validateFileType(String ext) {
        if (!List.of("pdf", "docx", "md", "markdown", "txt").contains(ext)) {
            throw new IllegalArgumentException("仅支持 PDF、DOCX、Markdown、TXT");
        }
    }

    private String extension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] getBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("读取上传文件失败", e);
        }
    }
}
