package com.vs.vsaiagent.controller;

import com.vs.vsaiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 通用文件下载端点。
 *
 * 把 Skill / Manus / 知识库等产物（PDF、图片、报告等）通过 HTTP 暴露给浏览器，
 * 避免前端只看得见 filePath 字符串但拿不到文件本身。
 *
 * 安全约束：path 必须落在 {@link FileConstant#FILE_SAVE_DIR} 根目录之下，
 * 防止路径穿越读取任意系统文件。
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileDownloadController {

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("path") String path) {
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Path root = Paths.get(FileConstant.FILE_SAVE_DIR).toAbsolutePath().normalize();
            Path target = Paths.get(path).toAbsolutePath().normalize();

            // 防 path traversal：必须在根目录之下
            if (!target.startsWith(root)) {
                log.warn("[file-download] reject path outside root: {}", target);
                return ResponseEntity.status(403).build();
            }

            File file = target.toFile();
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            String filename = file.getName();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String contentType = Files.probeContentType(target);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(file.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename)
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            log.error("[file-download] failed path={}", path, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
