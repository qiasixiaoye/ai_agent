package com.vs.vsaiagent.knowledgebase.controller;

import com.vs.vsaiagent.knowledgebase.service.KnowledgeBaseService;
import com.vs.vsaiagent.knowledgebase.vo.ApiResponse;
import com.vs.vsaiagent.knowledgebase.vo.DocumentStatusVO;
import com.vs.vsaiagent.knowledgebase.vo.DocumentUploadResponse;
import com.vs.vsaiagent.observability.enums.ExecutionStageType;
import com.vs.vsaiagent.observability.service.ExecutionTraceRecorder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/kb/documents")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ExecutionTraceRecorder traceRecorder;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, ExecutionTraceRecorder traceRecorder) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.traceRecorder = traceRecorder;
    }

    @PostMapping("/upload")
    public ApiResponse<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "source", required = false) String source,
                                                      @RequestParam(value = "tags", required = false) String tags) {
        Map<String, Object> input = fileInput(file, source, tags);
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("kb.upload", input, "knowledge-base");
        try {
            DocumentUploadResponse response = knowledgeBaseService.upload(file, source, tags);
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "document_upload", null,
                    input, response, System.currentTimeMillis() - startedAt, true, null);
            traceRecorder.success(requestId, response, startedAt);
            return ApiResponse.success(response);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentStatusVO> detail(@PathVariable String documentId) {
        return ApiResponse.success(knowledgeBaseService.getStatus(documentId));
    }

    @GetMapping
    public ApiResponse<List<DocumentStatusVO>> list(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResponse.success(knowledgeBaseService.listRecent(limit));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Boolean> delete(@PathVariable String documentId) {
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("kb.delete", Map.of("documentId", documentId), "knowledge-base");
        try {
            knowledgeBaseService.deleteDocument(documentId);
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "document_delete", null,
                    documentId, true, System.currentTimeMillis() - startedAt, true, null);
            traceRecorder.success(requestId, true, startedAt);
            return ApiResponse.success(true);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @PostMapping("/{documentId}/reprocess")
    public ApiResponse<Boolean> reprocess(@PathVariable String documentId) {
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("kb.reprocess", Map.of("documentId", documentId), "knowledge-base");
        try {
            knowledgeBaseService.reprocessDocument(documentId);
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "document_reprocess", null,
                    documentId, true, System.currentTimeMillis() - startedAt, true, null);
            traceRecorder.success(requestId, true, startedAt);
            return ApiResponse.success(true);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    @PostMapping("/index/rebuild")
    public ApiResponse<Boolean> rebuildIndex() {
        long startedAt = System.currentTimeMillis();
        String requestId = traceRecorder.start("kb.rebuild-index", Map.of("operation", "rebuild"), "knowledge-base");
        try {
            knowledgeBaseService.rebuildVectorIndex();
            traceRecorder.stage(requestId, ExecutionStageType.TOOL, "vector_index_rebuild", null,
                    null, true, System.currentTimeMillis() - startedAt, true, null);
            traceRecorder.success(requestId, true, startedAt);
            return ApiResponse.success(true);
        } catch (Exception e) {
            traceRecorder.fail(requestId, e, startedAt);
            throw e;
        }
    }

    private Map<String, Object> fileInput(MultipartFile file, String source, String tags) {
        return Map.of(
                "filename", file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                "size", file == null ? 0 : file.getSize(),
                "contentType", file == null || file.getContentType() == null ? "" : file.getContentType(),
                "source", source == null ? "" : source,
                "tags", tags == null ? "" : tags
        );
    }
}
