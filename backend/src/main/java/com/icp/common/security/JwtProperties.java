package com.icp.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置：密钥与过期时间全部配置化（application.yml -> icp.jwt.*）。
 */
@Component
@ConfigurationProperties(prefix = "icp.jwt")
public class JwtProperties {

    /** Base64 编码的 HS256 密钥（至少 32 字节）。 */
    private String secret;

    /** access token 有效期（秒），默认 2 小时。 */
    private long accessTtlSeconds = 7200L;

    /** refresh token 有效期（秒），默认 7 天。 */
    private long refreshTtlSeconds = 604800L;

    private String issuer = "icp-platform";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(long accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    public void setRefreshTtlSeconds(long refreshTtlSeconds) {
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
