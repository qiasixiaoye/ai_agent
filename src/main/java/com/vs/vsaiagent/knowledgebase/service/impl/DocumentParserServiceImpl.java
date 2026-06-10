package com.vs.vsaiagent.knowledgebase.service.impl;

import com.vs.vsaiagent.knowledgebase.service.DocumentParserService;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    private final Tika tika = new Tika();

    @Override
    public String parseText(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        try {
            if ("md".equals(ext) || "markdown".equals(ext) || "txt".equals(ext)) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            if ("pdf".equals(ext) || "docx".equals(ext)) {
                return tika.parseToString(new ByteArrayInputStream(bytes));
            }
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        } catch (Exception e) {
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    private String extension(String fileName) {
        int idx = fileName == null ? -1 : fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
