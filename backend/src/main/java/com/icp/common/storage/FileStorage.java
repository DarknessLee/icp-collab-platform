package com.icp.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：本地磁盘实现（M1），二期可替换 MinIO/S3。
 */
public interface FileStorage {

    /**
     * 保存文件，返回相对存储根的路径。
     */
    String store(MultipartFile file, String bizType);

    /**
     * 读取文件字节。
     */
    byte[] load(String relativePath);

    /**
     * 删除文件。
     */
    boolean delete(String relativePath);

    /**
     * 判断文件是否存在。
     */
    boolean exists(String relativePath);
}
