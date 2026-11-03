package com.icp.common.storage;

import com.icp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘文件存储：存储根默认为 &lt;repo&gt;/.runtime/files。
 */
@Service
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path root;

    public LocalFileStorage(@Value("${icp.storage.local-root:../.runtime/files}") String localRoot) {
        this.root = Paths.get(localRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new BusinessException("无法初始化本地存储目录: " + root);
        }
    }

    @Override
    public String store(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String type = StringUtils.hasText(bizType) ? bizType : "common";
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String suffix = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            suffix = original.substring(dot);
        }
        String relative = type + "/" + LocalDate.now().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法文件路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("保存文件失败: " + ex.getMessage());
        }
        return relative;
    }

    @Override
    public byte[] load(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException ex) {
            throw new BusinessException("读取文件失败: " + ex.getMessage());
        }
    }

    @Override
    public boolean delete(String relativePath) {
        try {
            return Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    private Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException("非法文件路径");
        }
        return path;
    }
}
