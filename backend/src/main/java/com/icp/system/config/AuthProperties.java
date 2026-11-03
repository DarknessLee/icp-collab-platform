package com.icp.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证策略配置：登录失败计数与锁定时长（M1 用常量配置，后续可迁移至 sys_config）。
 */
@Component
@ConfigurationProperties(prefix = "icp.auth")
public class AuthProperties {

    private int maxFailCount = 5;

    private int lockMinutes = 15;

    public int getMaxFailCount() {
        return maxFailCount;
    }

    public void setMaxFailCount(int maxFailCount) {
        this.maxFailCount = maxFailCount;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }
}
