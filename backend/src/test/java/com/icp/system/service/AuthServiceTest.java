package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.cache.CacheService;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.JwtTokenProvider;
import com.icp.system.config.AuthProperties;
import com.icp.system.dto.LoginRequest;
import com.icp.system.dto.LoginResponse;
import com.icp.system.dto.MenuNode;
import com.icp.system.dto.RefreshTokenRequest;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysMenu;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysLoginLogMapper;
import com.icp.system.mapper.SysMenuMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 认证服务单测：错误密码计数、锁定、锁定中拒绝、刷新类型校验、成功签发。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysDeptMapper deptMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysLoginLogMapper loginLogMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private CacheService cacheService;
    @Mock
    private ConfigService configService;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private OnlineSessionService onlineSessionService;

    private AuthService authService;
    private SysUser user;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setMaxFailCount(5);
        properties.setLockMinutes(15);
        authService = new AuthService(userMapper, deptMapper, roleMapper, menuMapper, loginLogMapper,
                passwordEncoder, tokenProvider, properties, cacheService, configService,
                passwordPolicyService, onlineSessionService);

        user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$hash");
        user.setStatus(1);
        user.setDeptId(8L);
        user.setFailCount(0);
        lenient().when(userMapper.selectOne(any())).thenReturn(user);
        lenient().when(configService.getInt(eq(AuthService.CONFIG_MAX_FAIL), anyInt())).thenReturn(5);
        lenient().when(configService.getInt(eq(AuthService.CONFIG_LOCK_MINUTES), anyInt())).thenReturn(15);
    }

    @Test
    void loginWithWrongPasswordIncrementsFailCount() {
        when(passwordEncoder.matches("bad", user.getPassword())).thenReturn(false);
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("bad");

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getCode())
                        .isEqualTo(ErrorCode.LOGIN_FAILED.getCode()));
    }

    @Test
    void loginLocksAccountAfterMaxFailures() {
        user.setFailCount(4);
        when(passwordEncoder.matches("bad", user.getPassword())).thenReturn(false);
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("bad");

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode().getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
                    Map<?, ?> data = (Map<?, ?>) businessException.getData();
                    assertThat(data.get("remainingSeconds")).isNotNull();
                });
    }

    @Test
    void loginRejectsLockedAccountWithRemainingSeconds() {
        user.setLockUntil(LocalDateTime.now().plusMinutes(10));
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("Icp@2026");

        assertThatThrownBy(() -> authService.login(request, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode().getCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED.getCode());
                    long remaining = (Long) ((Map<?, ?>) businessException.getData()).get("remainingSeconds");
                    assertThat(remaining).isBetween(1L, 600L);
                });
    }

    @Test
    void refreshRejectsAccessToken() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(tokenProvider.parse("access-token")).thenReturn(claims);
        when(tokenProvider.getType(claims)).thenReturn(JwtTokenProvider.TYPE_ACCESS);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("access-token");

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getCode())
                        .isEqualTo(ErrorCode.TOKEN_INVALID.getCode()));
    }

    @Test
    void loginSuccessIssuesTokensAndPermissions() {
        when(passwordEncoder.matches("Icp@2026", user.getPassword())).thenReturn(true);
        when(roleMapper.selectByUserId(1L)).thenReturn(new ArrayList<>());
        when(tokenProvider.createAccessToken(eq(1L), eq("admin"), eq(8L), any(), any()))
                .thenReturn("access-jwt");
        when(tokenProvider.createRefreshToken(eq(1L), eq("admin"), any())).thenReturn("refresh-jwt");
        when(tokenProvider.getAccessTtlSeconds()).thenReturn(7200L);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(tokenProvider.parse("access-jwt")).thenReturn(claims);
        when(tokenProvider.getJti(claims)).thenReturn("jti-1");
        SysDept dept = new SysDept();
        dept.setId(8L);
        dept.setDeptName("信息技术部");
        lenient().when(deptMapper.selectById(8L)).thenReturn(dept);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("Icp@2026");
        LoginResponse response = authService.login(request, null);

        assertThat(response.getAccessToken()).isEqualTo("access-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
        assertThat(response.getUser().getUsername()).isEqualTo("admin");
        assertThat(response.getUser().getDeptName()).isEqualTo("信息技术部");
        assertThat(response.getPermissions()).isEmpty();
        assertThat(response.getMenus()).isEqualTo(List.of());
    }

    @Test
    void loginMenuTreeFiltersInvisibleMenus() {
        when(passwordEncoder.matches("Icp@2026", user.getPassword())).thenReturn(true);
        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleCode("ICP_MANAGER");
        when(roleMapper.selectByUserId(1L)).thenReturn(new ArrayList<>(List.of(role)));
        SysMenu parent = menu(20L, 0L, "流程中心", "M", 1, null);
        SysMenu hidden = menu(40L, 20L, "表单管理", "C", 0, "/wf/form");
        SysMenu visible = menu(41L, 20L, "待办中心", "C", 1, "/wf/todo");
        when(menuMapper.selectByRoleIds(any())).thenReturn(List.of(parent, hidden, visible));
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(tokenProvider.createAccessToken(eq(1L), eq("admin"), eq(8L), any(), any()))
                .thenReturn("access-jwt");
        when(tokenProvider.createRefreshToken(eq(1L), eq("admin"), any())).thenReturn("refresh-jwt");
        when(tokenProvider.getAccessTtlSeconds()).thenReturn(7200L);
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(tokenProvider.parse("access-jwt")).thenReturn(claims);
        when(tokenProvider.getJti(claims)).thenReturn("jti-1");

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("Icp@2026");
        LoginResponse response = authService.login(request, null);

        assertThat(response.getMenus()).hasSize(1);
        MenuNode root = response.getMenus().get(0);
        assertThat(root.getId()).isEqualTo(20L);
        assertThat(root.getChildren()).extracting(MenuNode::getId).containsExactly(41L);
        assertThat(root.getChildren().get(0).getVisible()).isEqualTo(1);
    }

    private static SysMenu menu(Long id, Long parentId, String name, String type, Integer visible, String path) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setVisible(visible);
        menu.setPath(path);
        menu.setOrderNum(1);
        return menu;
    }
}

