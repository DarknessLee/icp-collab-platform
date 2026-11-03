package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.cache.CacheService;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.system.entity.SysMenu;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysRoleMenu;
import com.icp.system.entity.SysUserRole;
import com.icp.system.mapper.SysMenuMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysRoleMenuMapper;
import com.icp.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理：内置 6 角色不可删除、不可改 role_code；菜单授权整树覆盖并补齐祖先节点。
 */
@Service
public class RoleService {

    private static final Set<String> BUILT_IN_CODES = Set.of(
            "ADMIN", "ICP_MANAGER", "DEPT_MANAGER", "EMPLOYEE", "AUDITOR", "MANAGEMENT");

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final ChangeLogService changeLogService;
    private final CacheService cacheService;

    public RoleService(SysRoleMapper roleMapper, SysRoleMenuMapper roleMenuMapper,
                       SysUserRoleMapper userRoleMapper, SysMenuMapper menuMapper,
                       ChangeLogService changeLogService, CacheService cacheService) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.menuMapper = menuMapper;
        this.changeLogService = changeLogService;
        this.cacheService = cacheService;
    }

    /** 角色菜单/角色状态变化后，登录权限树缓存（auth:menus:*）必须整体失效。 */
    private void evictAuthMenusCache() {
        cacheService.evictByPrefix("auth:menus:");
    }

    public PageResult<SysRole> page(long pageNum, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getRoleSort)
                .orderByAsc(SysRole::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysRole::getRoleName, keyword.trim())
                    .or().like(SysRole::getRoleCode, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        Page<SysRole> page = roleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        for (SysRole role : page.getRecords()) {
            role.setUserCount(userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, role.getId())).longValue());
        }
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public SysRole get(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        role.setMenuIds(menuIdsOf(id));
        role.setUserCount(userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id)).longValue());
        return role;
    }

    @Transactional
    public SysRole create(SysRole request) {
        validate(request);
        String code = request.getRoleCode().trim().toUpperCase();
        SysRole existing = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code).last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码已存在");
        }
        checkDataScope(request.getDataScope());
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(request.getRoleName().trim());
        role.setRoleSort(request.getRoleSort() == null ? 0 : request.getRoleSort());
        role.setDataScope(request.getDataScope() == null ? 1 : request.getDataScope());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        role.setRemark(request.getRemark());
        // 唯一编码可能被逻辑删除的历史行占用：复用该行，避免 DuplicateKeyException 冒 500
        SysRole deleted = roleMapper.selectByCodeIncludeDeleted(code);
        if (deleted != null) {
            role.setId(deleted.getId());
            roleMapper.reviveRole(role, SecurityUtils.currentUserIdOrNull());
            // 恢复的角色不保留逻辑删除前的旧菜单授权，避免“复活即拥有旧权限”
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                    .eq(SysRoleMenu::getRoleId, role.getId()));
            evictAuthMenusCache();
            changeLogService.record("sys_role", role.getId(), null, role);
            return get(role.getId());
        }
        roleMapper.insert(role);
        evictAuthMenusCache();
        changeLogService.record("sys_role", role.getId(), null, role);
        return role;
    }

    @Transactional
    public SysRole update(Long id, SysRole request) {
        SysRole existing = get(id);
        SysRole before = copyOf(existing);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        boolean builtIn = isBuiltIn(existing);
        if (builtIn && StringUtils.hasText(request.getRoleCode())
                && !request.getRoleCode().trim().equalsIgnoreCase(existing.getRoleCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内置角色不允许修改角色编码");
        }
        if (!builtIn && StringUtils.hasText(request.getRoleCode())) {
            String code = request.getRoleCode().trim().toUpperCase();
            SysRole byCode = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, code).last("LIMIT 1"));
            if (byCode != null && !byCode.getId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码已存在");
            }
            existing.setRoleCode(code);
        }
        existing.setRoleName(StringUtils.hasText(request.getRoleName())
                ? request.getRoleName().trim() : existing.getRoleName());
        existing.setRoleSort(request.getRoleSort() == null ? existing.getRoleSort() : request.getRoleSort());
        if (request.getDataScope() != null) {
            checkDataScope(request.getDataScope());
            existing.setDataScope(request.getDataScope());
        }
        existing.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        roleMapper.updateById(existing);
        evictAuthMenusCache();
        changeLogService.record("sys_role", id, before, existing);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = get(id);
        if (isBuiltIn(role)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "内置角色不允许删除");
        }
        long users = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (users > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "角色下存在用户，不能删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        evictAuthMenusCache();
        changeLogService.record("sys_role", id, role, null);
    }

    public List<Long> menuIdsOf(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Transactional
    public void updateMenus(Long roleId, List<Long> menuIds) {
        SysRole role = get(roleId);
        SysRole before = new SysRole();
        before.setId(roleId);
        before.setMenuIds(menuIdsOf(roleId));
        Set<Long> target = expandWithAncestors(menuIds);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : target) {
            SysRoleMenu roleMenu = new SysRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
        evictAuthMenusCache();
        SysRole after = new SysRole();
        after.setId(roleId);
        after.setMenuIds(new ArrayList<>(target));
        Map<String, Object> change = new HashMap<>();
        change.put("roleId", roleId);
        change.put("roleName", role.getRoleName());
        change.put("before", before.getMenuIds());
        change.put("after", after.getMenuIds());
        changeLogService.record("sys_role_menu", roleId, change.get("before"), change.get("after"));
    }

    private Set<Long> expandWithAncestors(List<Long> menuIds) {
        Set<Long> result = new LinkedHashSet<>();
        if (menuIds == null || menuIds.isEmpty()) {
            return result;
        }
        List<SysMenu> all = menuMapper.selectList(null);
        Map<Long, SysMenu> byId = new HashMap<>();
        for (SysMenu menu : all) {
            byId.put(menu.getId(), menu);
        }
        for (Long menuId : menuIds) {
            if (menuId == null) {
                continue;
            }
            SysMenu menu = byId.get(menuId);
            if (menu == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单不存在: " + menuId);
            }
            List<Long> chain = new ArrayList<>();
            Long current = menuId;
            int guard = 0;
            while (current != null && current != 0L && guard++ < 32) {
                chain.add(current);
                SysMenu node = byId.get(current);
                if (node == null) {
                    break;
                }
                current = node.getParentId();
            }
            result.addAll(chain);
        }
        return result;
    }

    private boolean isBuiltIn(SysRole role) {
        return role != null && BUILT_IN_CODES.contains(String.valueOf(role.getRoleCode()).toUpperCase());
    }

    private void checkDataScope(Integer dataScope) {
        if (dataScope == null) {
            return;
        }
        if (dataScope < 1 || dataScope > 4) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "数据范围仅支持 1-4（5 自定义本期不启用）");
        }
    }

    private void validate(SysRole request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getRoleCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色编码不能为空");
        }
        if (!StringUtils.hasText(request.getRoleName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色名称不能为空");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色状态不合法");
        }
        checkDataScope(request.getDataScope());
    }

    private SysRole copyOf(SysRole source) {
        SysRole copy = new SysRole();
        copy.setId(source.getId());
        copy.setRoleCode(source.getRoleCode());
        copy.setRoleName(source.getRoleName());
        copy.setRoleSort(source.getRoleSort());
        copy.setDataScope(source.getDataScope());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }
}

