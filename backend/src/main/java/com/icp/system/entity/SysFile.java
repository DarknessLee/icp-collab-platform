package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {

    private String fileName;
    private String originalName;
    private String fileSuffix;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String storageType;
    private String bucket;
    private String bizType;
    private Long bizId;
    private Long uploadBy;
}
