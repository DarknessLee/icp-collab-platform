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
 * JWT 生成与解析（HS256）。payload 至少包含 userId / username / roles。
 */
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }

    public String createAccessToken(Long userId, String username, List<String> roles) {
        return createToken(userId, username, roles, TYPE_ACCESS, properties.getAccessTtlSeconds());
    }

    public String createRefreshToken(Long userId, String username, List<String> roles) {
        return createToken(userId, username, roles, TYPE_REFRESH, properties.getRefreshTtlSeconds());
    }

    private String createToken(Long userId, String username, List<String> roles, String type, long ttlSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles == null ? new ArrayList<String>() : roles)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
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

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        Object value = claims.get(CLAIM_ROLES);
        if (value instanceof List<?> list) {
            List<String> roles = new ArrayList<>(list.size());
            for (Object item : list) {
                roles.add(String.valueOf(item));
            }
            return roles;
        }
        return new ArrayList<>();
    }

    public String getType(Claims claims) {
        return String.valueOf(claims.get(CLAIM_TYPE));
    }

    public long getAccessTtlSeconds() {
        return properties.getAccessTtlSeconds();
    }
}
