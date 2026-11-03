package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.common.storage.FileStorage;
import com.icp.system.entity.SysFile;
import com.icp.system.mapper.SysFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 附件：白名单 + 大小限制（sys_config: sys.upload.maxSize / sys.upload.allowExt），
 * 元数据落 sys_file，字节落本地 FileStorage（.runtime/files）。
 */
@Service
public class FileService {

    /** docs/03 §1.7 契约白名单，与 sys_config 白名单取并集。 */
    private static final Set<String> CONTRACT_ALLOWED = new LinkedHashSet<>(Arrays.asList(
            "pdf", "png", "jpg", "jpeg", "doc", "docx", "xls", "xlsx", "zip"));

    private static final Set<String> PREVIEW_INLINE = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    private final SysFileMapper fileMapper;
    private final FileStorage fileStorage;
    private final ConfigService configService;

    public FileService(SysFileMapper fileMapper, FileStorage fileStorage, ConfigService configService) {
        this.fileMapper = fileMapper;
        this.fileStorage = fileStorage;
        this.configService = configService;
    }

    @Transactional
    public SysFile store(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "上传文件不能为空");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim() : "unnamed";
        String suffix = suffixOf(originalName);
        if (!allowedSuffixes().contains(suffix)) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "不支持的文件类型: " + (suffix.isEmpty() ? "(无扩展名)" : suffix));
        }
        long maxBytes = maxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "文件超过大小限制: " + maxSizeMb() + "MB");
        }
        String effectiveBizType = StringUtils.hasText(bizType) ? bizType.trim() : "common";
        String relativePath = fileStorage.store(file, effectiveBizType);
        SysFile record = new SysFile();
        record.setFileName(originalName);
        record.setOriginalName(originalName);
        record.setFileSuffix(suffix);
        record.setFilePath(relativePath);
        record.setFileSize(file.getSize());
        record.setContentType(StringUtils.hasText(file.getContentType())
                ? file.getContentType() : "application/octet-stream");
        record.setStorageType(configService.get("sys.file.storageType").orElse("local"));
        record.setBucket(null);
        record.setBizType(effectiveBizType);
        record.setBizId(bizId);
        record.setUploadBy(SecurityUtils.currentUserIdOrNull());
        fileMapper.insert(record);
        return record;
    }

    public SysFile get(Long id) {
        SysFile file = fileMapper.selectById(id);
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return file;
    }

    public byte[] loadBytes(SysFile file) {
        if (!fileStorage.exists(file.getFilePath())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件实体已丢失");
        }
        return fileStorage.load(file.getFilePath());
    }

    public boolean previewable(SysFile file) {
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        return PREVIEW_INLINE.contains(contentType);
    }

    private Set<String> allowedSuffixes() {
        Set<String> allowed = new LinkedHashSet<>(CONTRACT_ALLOWED);
        configService.get("sys.upload.allowExt").ifPresent(value -> {
            for (String part : value.split(",")) {
                String ext = part.trim().toLowerCase(Locale.ROOT);
                if (!ext.isEmpty()) {
                    allowed.add(ext);
                }
            }
        });
        return allowed;
    }

    private long maxSizeMb() {
        return Math.max(1, configService.getInt("sys.upload.maxSize", 20));
    }

    private String suffixOf(String originalName) {
        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
