package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.cache.CacheService;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.JwtTokenProvider;
import com.icp.common.security.SecurityUtils;
import com.icp.common.security.UserPrincipal;
import com.icp.system.config.AuthProperties;
import com.icp.system.dto.LoginRequest;
import com.icp.system.dto.LoginResponse;
import com.icp.system.dto.MeResponse;
import com.icp.system.dto.MenuNode;
import com.icp.system.dto.RefreshTokenRequest;
import com.icp.system.dto.UserInfo;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysLoginLog;
import com.icp.system.entity.SysMenu;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysLoginLogMapper;
import com.icp.system.mapper.SysMenuMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务：登录 / 当前用户 / 刷新令牌 / 退出。
 * 登录失败计数与锁定策略由 icp.auth.* 配置（M1）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String MENU_CACHE_PREFIX = "auth:menus:";
    private static final long MENU_CACHE_TTL_SECONDS = 300L;

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthProperties authProperties;
    private final CacheService cacheService;

    public AuthService(SysUserMapper userMapper, SysDeptMapper deptMapper, SysRoleMapper roleMapper,
                       SysMenuMapper menuMapper, SysLoginLogMapper loginLogMapper, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, AuthProperties authProperties, CacheService cacheService) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authProperties = authProperties;
        this.cacheService = cacheService;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername().trim();
        SysUser user = findByUsername(username);
        if (user == null) {
            writeLoginLog(username, null, "login", 0, "用户不存在", httpRequest);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(now)) {
            writeLoginLog(username, user.getId(), "login", 0, "账号锁定中", httpRequest);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            writeLoginLog(username, user.getId(), "login", 0, "账号已停用", httpRequest);
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "账号已停用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int failCount = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
            boolean locked = failCount >= authProperties.getMaxFailCount();
            LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getFailCount, failCount)
                    .set(SysUser::getUpdateTime, now);
            if (locked) {
                update.set(SysUser::getLockUntil, now.plusMinutes(authProperties.getLockMinutes()));
            }
            userMapper.update(null, update);
            writeLoginLog(username, user.getId(), "login", 0,
                    locked ? "密码错误，账号已锁定" : "密码错误", httpRequest);
            if (locked) {
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLockUntil, null)
                .set(SysUser::getLoginTime, now)
                .set(SysUser::getLoginIp, clientIp(httpRequest))
                .set(SysUser::getUpdateTime, now);
        userMapper.update(null, update);
        user.setLoginTime(now);
        user.setFailCount(0);
        user.setLockUntil(null);
        writeLoginLog(username, user.getId(), "login", 1, "登录成功", httpRequest);

        return buildLoginResponse(user);
    }

    public MeResponse me() {
        UserPrincipal principal = SecurityUtils.currentUser();
        SysUser user = userMapper.selectById(principal.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        MeResponse response = new MeResponse();
        response.setUser(toUserInfo(user));
        List<SysRole> roles = rolesOf(user.getId());
        response.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        List<SysMenu> menus = menusOf(roles);
        response.setPermissions(permissionsOf(menus));
        response.setMenus(buildMenuTree(menus));
        return response;
    }

    public LoginResponse refresh(RefreshTokenRequest request) {
        Claims claims = tokenProvider.parse(request.getRefreshToken());
        if (!JwtTokenProvider.TYPE_REFRESH.equals(tokenProvider.getType(claims))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "请使用 refreshToken 刷新");
        }
        Long userId = tokenProvider.getUserId(claims);
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "用户不存在或已停用");
        }
        return buildLoginResponse(user);
    }

    public void logout() {
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        if (principal != null) {
            SysLoginLog logEntity = new SysLoginLog();
            logEntity.setUsername(principal.getUsername());
            logEntity.setUserId(principal.getUserId());
            logEntity.setLoginType("logout");
            logEntity.setStatus(1);
            logEntity.setMsg("退出登录");
            logEntity.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(logEntity);
            cacheService.evict(MENU_CACHE_PREFIX + principal.getUserId());
        }
    }

    private LoginResponse buildLoginResponse(SysUser user) {
        List<SysRole> roles = rolesOf(user.getId());
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
        List<SysMenu> menus = menusOf(roles);
        List<String> permissions = permissionsOf(menus);
        LoginResponse response = new LoginResponse();
        response.setAccessToken(tokenProvider.createAccessToken(user.getId(), user.getUsername(), roleCodes));
        response.setRefreshToken(tokenProvider.createRefreshToken(user.getId(), user.getUsername(), roleCodes));
        response.setExpiresIn(tokenProvider.getAccessTtlSeconds());
        response.setUser(toUserInfo(user));
        response.setRoles(roleCodes);
        response.setPermissions(permissions);
        response.setMenus(buildMenuTree(menus));
        return response;
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
    }

    private List<SysRole> rolesOf(Long userId) {
        List<SysRole> roles = roleMapper.selectByUserId(userId);
        return roles == null ? new ArrayList<>() : roles;
    }

    @SuppressWarnings("unchecked")
    private List<SysMenu> menusOf(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getId).sorted().collect(Collectors.toList());
        String cacheKey = MENU_CACHE_PREFIX + roleIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        Object cached = cacheService.get(cacheKey).orElse(null);
        if (cached instanceof List<?> list) {
            return (List<SysMenu>) list;
        }
        List<SysMenu> menus = menuMapper.selectByRoleIds(roleIds);
        if (menus == null) {
            menus = new ArrayList<>();
        }
        cacheService.put(cacheKey, menus, MENU_CACHE_TTL_SECONDS);
        return menus;
    }

    private List<String> permissionsOf(List<SysMenu> menus) {
        Set<String> permissions = new LinkedHashSet<>();
        for (SysMenu menu : menus) {
            if (StringUtils.hasText(menu.getPerms())) {
                permissions.add(menu.getPerms());
            }
        }
        return new ArrayList<>(permissions);
    }

    private List<MenuNode> buildMenuTree(List<SysMenu> menus) {
        List<SysMenu> routeMenus = menus.stream()
                .filter(menu -> "M".equals(menu.getMenuType()) || "C".equals(menu.getMenuType()))
                .sorted(Comparator.comparing(menu -> menu.getOrderNum() == null ? 0 : menu.getOrderNum()))
                .collect(Collectors.toList());
        Map<Long, MenuNode> nodes = new LinkedHashMap<>();
        for (SysMenu menu : routeMenus) {
            MenuNode node = new MenuNode();
            node.setId(menu.getId());
            node.setParentId(menu.getParentId());
            node.setName(menu.getMenuName());
            node.setPath(menu.getPath());
            node.setComponent(menu.getComponent());
            node.setIcon(menu.getIcon());
            node.setType(menu.getMenuType());
            node.setPerms(menu.getPerms());
            node.setOrderNum(menu.getOrderNum());
            nodes.put(menu.getId(), node);
        }
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes.values()) {
            MenuNode parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private UserInfo toUserInfo(SysUser user) {
        String deptName = null;
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            if (dept != null) {
                deptName = dept.getDeptName();
            }
        }
        return new UserInfo(user.getId(), user.getUsername(), user.getRealName(), user.getDeptId(), deptName);
    }

    private void writeLoginLog(String username, Long userId, String loginType, int status, String msg,
                               HttpServletRequest request) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setUsername(username);
            entity.setUserId(userId);
            entity.setLoginType(loginType);
            entity.setStatus(status);
            entity.setMsg(msg);
            entity.setLoginTime(LocalDateTime.now());
            if (request != null) {
                entity.setIp(clientIp(request));
                entity.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            }
            loginLogMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("write sys_login_log failed: {}", ex.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
