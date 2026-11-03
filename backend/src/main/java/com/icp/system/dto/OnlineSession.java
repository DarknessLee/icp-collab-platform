package com.icp.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 在线会话信息（内存注册表，M2a）。 */
@Data
public class OnlineSession {

    private String jti;
    private Long userId;
    private String username;
    private String realName;
    private String deptName;
    private String ip;
    private String userAgent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActiveTime;
    private LocalDateTime expireTime;
}
