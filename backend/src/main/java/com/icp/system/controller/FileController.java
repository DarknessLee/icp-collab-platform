package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.audit.AuditLog;
import com.icp.common.exception.BusinessException;
import com.icp.common.api.ErrorCode;
import com.icp.system.entity.SysFile;
import com.icp.system.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 附件上传/下载/预览。种子权限无 system:file:*，按任务口径仅要求登录（Bearer 有效）。
 */
@Tag(name = "附件管理")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传附件（multipart 字段名 file）")
    @PostMapping
    @AuditLog(module = "附件管理", action = "上传附件", businessType = "sys_file")
    public ApiResponse<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId) {
        SysFile saved = fileService.store(file, bizType, bizId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", saved.getId());
        data.put("originalName", saved.getOriginalName());
        data.put("size", saved.getFileSize());
        data.put("suffix", saved.getFileSuffix());
        data.put("contentType", saved.getContentType());
        return ApiResponse.ok(data);
    }

    @Operation(summary = "附件元数据")
    @GetMapping("/{id}")
    public ApiResponse<SysFile> metadata(@PathVariable Long id) {
        return ApiResponse.ok(fileService.get(id));
    }

    @Operation(summary = "下载附件")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        SysFile file = fileService.get(id);
        byte[] bytes = fileService.loadBytes(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + encode(file.getOriginalName()))
                .contentType(mediaType(file.getContentType()))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @Operation(summary = "预览附件（仅图片/pdf 内联，其余 41301）")
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long id) {
        SysFile file = fileService.get(id);
        if (!fileService.previewable(file)) {
            throw new BusinessException(ErrorCode.FILE_UNSUPPORTED, "该文件类型不支持在线预览");
        }
        byte[] bytes = fileService.loadBytes(file);
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''"
                        + encode(file.getOriginalName()))
                .contentType(mediaType(file.getContentType()))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String encode(String name) {
        String safe = name == null || name.isEmpty() ? "file" : name;
        return URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
