package com.icp.common.security;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * JWT 生成与解析（HS256）。payload 包含 userId / username / deptId / roles / perms / type / jti。
 * access token 携带权限标识列表，便于无状态鉴权；refresh token 只用类型区分，刷新时重新加载权限。
 */
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_DEPT_ID = "deptId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";
    private static final String CLAIM_TYPE = "type";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }

    public String createAccessToken(Long userId, String username, List<String> roles) {
        return createAccessToken(userId, username, null, roles, List.of());
    }

    public String createAccessToken(Long userId, String username, Long deptId,
                                    List<String> roles, List<String> permissions) {
        return createToken(userId, username, deptId, roles, permissions, TYPE_ACCESS,
                properties.getAccessTtlSeconds());
    }

    public String createRefreshToken(Long userId, String username, List<String> roles) {
        return createToken(userId, username, null, roles, List.of(), TYPE_REFRESH,
                properties.getRefreshTtlSeconds());
    }

    private String createToken(Long userId, String username, Long deptId, List<String> roles,
                               List<String> permissions, String type, long ttlSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlSeconds * 1000L);
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_DEPT_ID, deptId)
                .claim(CLAIM_ROLES, roles == null ? new ArrayList<String>() : roles)
                .claim(CLAIM_PERMS, permissions == null ? new ArrayList<String>() : permissions)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "令牌无效或已过期");
        }
    }

    public Long getUserId(Claims claims) {
        Object value = claims.get(CLAIM_USER_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(claims.getSubject()));
    }

    public String getUsername(Claims claims) {
        return String.valueOf(claims.get(CLAIM_USERNAME));
    }

    public Long getDeptId(Claims claims) {
        Object value = claims.get(CLAIM_DEPT_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        return toStringList(claims.get(CLAIM_ROLES));
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        return toStringList(claims.get(CLAIM_PERMS));
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return new ArrayList<>();
    }

    public String getType(Claims claims) {
        return String.valueOf(claims.get(CLAIM_TYPE));
    }

    public long getAccessTtlSeconds() {
        return properties.getAccessTtlSeconds();
    }

    public long getRefreshTtlSeconds() {
        return properties.getRefreshTtlSeconds();
    }
}
