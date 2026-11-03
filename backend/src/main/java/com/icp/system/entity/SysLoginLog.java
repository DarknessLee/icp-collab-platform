package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {

    private String username;
    private Long userId;
    private String ip;
    private String userAgent;
    private String loginType;
    private Integer status;
    private String msg;
    private LocalDateTime loginTime;
}
