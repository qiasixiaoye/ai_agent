package com.vs.vsaiagent.workflowbuilder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 生成的 DSL YAML 落盘与读取，支撑 export 接口。
 *
 * 文件位置：{user.dir}/tmp/workflow-builder/{workflowId}.yml
 * workflowId 仅允许 UUID 格式，避免路径穿越。
 */
@Slf4j
@Service
public class WorkflowFileService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Path baseDir;

    public WorkflowFileService() {
        this(Path.of(System.getProperty("user.dir"), "tmp", "workflow-builder"));
    }

    public WorkflowFileService(Path baseDir) {
        this.baseDir = baseDir;
    }

    public Path save(String workflowId, String dslYaml) {
        requireValidId(workflowId);
        try {
            Files.createDirectories(baseDir);
            Path file = baseDir.resolve(workflowId + ".yml");
            Files.writeString(file, dslYaml, StandardCharsets.UTF_8);
            log.info("[workflow-builder] DSL saved: {}", file);
            return file;
        } catch (IOException e) {
            throw new IllegalStateException("保存 DSL 文件失败: " + e.getMessage(), e);
        }
    }

    public Optional<String> load(String workflowId) {
        requireValidId(workflowId);
        Path file = baseDir.resolve(workflowId + ".yml");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取 DSL 文件失败: " + e.getMessage(), e);
        }
    }

    private void requireValidId(String workflowId) {
        if (workflowId == null || !UUID_PATTERN.matcher(workflowId).matches()) {
            throw new IllegalArgumentException("非法 workflowId（要求 UUID 格式）");
        }
    }
}
