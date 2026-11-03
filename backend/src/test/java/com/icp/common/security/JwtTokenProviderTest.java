package com.icp.common.security;

import com.icp.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("aWNwLXBsYXRmb3JtLWRlbW8tc2VjcmV0LWtleS0yMDI2LXNwcmluZy1ib290LWp3dC1oczI1Ng==");
        properties.setAccessTtlSeconds(7200);
        properties.setRefreshTtlSeconds(604800);
        properties.setIssuer("icp-platform-test");
        provider = new JwtTokenProvider(properties);
    }

    @Test
    void accessTokenCarriesUserIdUsernameRolesAndType() {
        String token = provider.createAccessToken(101L, "admin", List.of("ADMIN", "ICP_ADMIN"));
        Claims claims = provider.parse(token);

        assertEquals(101L, provider.getUserId(claims));
        assertEquals("admin", provider.getUsername(claims));
        assertEquals(List.of("ADMIN", "ICP_ADMIN"), provider.getRoles(claims));
        assertEquals(JwtTokenProvider.TYPE_ACCESS, provider.getType(claims));
        assertEquals("icp-platform-test", claims.getIssuer());
        assertTrue(claims.getExpiration().getTime() - claims.getIssuedAt().getTime() > 7000L * 1000L);
    }

    @Test
    void refreshTokenUsesRefreshType() {
        String token = provider.createRefreshToken(101L, "admin", List.of("ADMIN"));
        Claims claims = provider.parse(token);
        assertEquals(JwtTokenProvider.TYPE_REFRESH, provider.getType(claims));
        assertNotNull(claims.getExpiration());
    }

    @Test
    void tamperedTokenRejected() {
        String token = provider.createAccessToken(101L, "admin", List.of("ADMIN"));
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(BusinessException.class, () -> provider.parse(tampered));
    }
}
