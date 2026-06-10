package com.vs.vsaiagent.knowledgebase.service;

import com.vs.vsaiagent.knowledgebase.vo.DocumentStatusVO;
import com.vs.vsaiagent.knowledgebase.vo.DocumentUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService {
    DocumentUploadResponse upload(MultipartFile file, String source, String tags);

    DocumentStatusVO getStatus(String documentId);

    List<DocumentStatusVO> listRecent(int limit);

    void deleteDocument(String documentId);

    void reprocessDocument(String documentId);

    void rebuildVectorIndex();
}
