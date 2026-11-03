package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {

    private String traceId;
    private String title;
    private String businessType;
    private String method;
    private String requestMethod;
    private String requestUri;
    private String requestParam;
    private Integer status;
    private String errorMsg;
    private Long operUserId;
    private String operName;
    private Long deptId;
    private String operIp;
    private String userAgent;
    private Integer costMs;
    private LocalDateTime operTime;
}
