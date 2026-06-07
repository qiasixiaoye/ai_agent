package com.vs.vsaiagent.rag;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


/**
 * 文档加载器。从 classpath:document/*.md 加载预置知识。
 *
 * 当前默认加载样例：恋爱常见问题（保留作示例素材，可随时替换为业务文档）。
 */
@Component
@Slf4j
public class AssistantAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public AssistantAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                // 兼容历史命名：提取文件名倒数第 3、第 2 个字符作为状态标签
                String status = filename.length() >= 7
                        ? filename.substring(filename.length() - 6, filename.length() - 4)
                        : "default";
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }

        } catch (Exception e) {
            log.error("加载Markdown文件出错", e);
        }
        return allDocuments;
    }

}
