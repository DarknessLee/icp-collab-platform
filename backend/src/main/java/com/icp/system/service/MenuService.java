package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.cache.CacheService;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysMenu;
import com.icp.system.entity.SysRoleMenu;
import com.icp.system.mapper.SysMenuMapper;
import com.icp.system.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 菜单管理：M 目录 / C 菜单 / F 按钮，树形组装、祖先校验、删除引用校验。
 */
@Service
public class MenuService {

    private static final Set<String> TYPES = Set.of("M", "C", "F");

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final ChangeLogService changeLogService;
    private final CacheService cacheService;

    public MenuService(SysMenuMapper menuMapper, SysRoleMenuMapper roleMenuMapper,
                       ChangeLogService changeLogService, CacheService cacheService) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.changeLogService = changeLogService;
        this.cacheService = cacheService;
    }

    /** 菜单（权限标识）变化后，登录权限树缓存必须整体失效。 */
    private void evictAuthMenusCache() {
        cacheService.evictByPrefix("auth:menus:");
    }

    public List<SysMenu> list(Long parentId, String menuType, String keyword) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getParentId)
                .orderByAsc(SysMenu::getOrderNum)
                .orderByAsc(SysMenu::getId);
        if (parentId != null) {
            wrapper.eq(SysMenu::getParentId, parentId);
        }
        if (StringUtils.hasText(menuType)) {
            wrapper.eq(SysMenu::getMenuType, menuType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysMenu::getMenuName, keyword.trim())
                    .or().like(SysMenu::getPerms, keyword.trim())
                    .or().like(SysMenu::getPath, keyword.trim()));
        }
        return menuMapper.selectList(wrapper);
    }

    public List<SysMenu> tree() {
        List<SysMenu> all = list(null, null, null);
        Map<Long, SysMenu> byId = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            menu.setChildren(new ArrayList<>());
            byId.put(menu.getId(), menu);
        }
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu menu : all) {
            Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
            if (parentId == 0L || !byId.containsKey(parentId)) {
                roots.add(menu);
            } else {
                byId.get(parentId).getChildren().add(menu);
            }
        }
        return roots;
    }

    public SysMenu get(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }

    @Transactional
    public SysMenu create(SysMenu request) {
        validate(request, null);
        SysMenu menu = new SysMenu();
        copyEditable(request, menu);
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        if (menu.getStatus() == null) {
            menu.setStatus(1);
        }
        if (menu.getOrderNum() == null) {
            menu.setOrderNum(0);
        }
        menuMapper.insert(menu);
        evictAuthMenusCache();
        changeLogService.record("sys_menu", menu.getId(), null, menu);
        return menu;
    }

    @Transactional
    public SysMenu update(Long id, SysMenu request) {
        SysMenu existing = get(id);
        SysMenu before = snapshot(existing);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (request.getParentId() != null && request.getParentId().equals(id)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上级菜单不能是自身");
        }
        if (request.getParentId() != null) {
            checkNotDescendant(id, request.getParentId());
            existing.setParentId(request.getParentId());
        }
        copyEditable(request, existing);
        validate(existing, id);
        menuMapper.updateById(existing);
        evictAuthMenusCache();
        changeLogService.record("sys_menu", id, before, existing);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysMenu existing = get(id);
        long children = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "存在子菜单，不能删除");
        }
        long references = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getMenuId, id));
        if (references > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "菜单已被角色引用，不能删除");
        }
        menuMapper.deleteById(id);
        evictAuthMenusCache();
        changeLogService.record("sys_menu", id, existing, null);
    }

    private void validate(SysMenu request, Long selfId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        String type = request.getMenuType() == null ? "" : request.getMenuType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单类型仅支持 M/C/F");
        }
        if (!StringUtils.hasText(request.getMenuName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单名称不能为空");
        }
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        SysMenu parent = null;
        if (parentId != 0L) {
            parent = menuMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级菜单不存在");
            }
        }
        if ("F".equals(type)) {
            if (parent == null || !"C".equalsIgnoreCase(String.valueOf(parent.getMenuType()))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "按钮必须挂在菜单(C)节点下");
            }
            if (!StringUtils.hasText(request.getPerms())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "按钮权限标识不能为空");
            }
        }
        if (!"F".equals(type) && !StringUtils.hasText(request.getPath())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "路由地址不能为空");
        }
        String normalizedPerms = StringUtils.hasText(request.getPerms()) ? request.getPerms().trim() : null;
        if (normalizedPerms != null) {
            LambdaQueryWrapper<SysMenu> duplicate = new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getPerms, normalizedPerms)
                    .last("LIMIT 1");
            if (selfId != null) {
                duplicate.ne(SysMenu::getId, selfId);
            }
            if (menuMapper.selectOne(duplicate) != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "权限标识已存在: " + normalizedPerms);
            }
        }
    }

    private void checkNotDescendant(Long id, Long candidateParentId) {
        Set<Long> visited = new HashSet<>();
        Long current = candidateParentId;
        while (current != null && current != 0L && visited.add(current)) {
            if (current.equals(id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级菜单不能是自身的子节点");
            }
            SysMenu node = menuMapper.selectById(current);
            current = node == null ? 0L : node.getParentId();
        }
    }

    private void copyEditable(SysMenu source, SysMenu target) {
        target.setMenuName(source.getMenuName() == null ? target.getMenuName() : source.getMenuName().trim());
        target.setMenuType(source.getMenuType() == null ? target.getMenuType()
                : source.getMenuType().trim().toUpperCase(Locale.ROOT));
        if (source.getPath() != null) {
            target.setPath(source.getPath());
        }
        if (source.getComponent() != null) {
            target.setComponent(source.getComponent());
        }
        if (source.getPerms() != null) {
            target.setPerms(source.getPerms().trim().isEmpty() ? null : source.getPerms().trim());
        }
        if (source.getIcon() != null) {
            target.setIcon(source.getIcon());
        }
        if (source.getOrderNum() != null) {
            target.setOrderNum(source.getOrderNum());
        }
        if (source.getVisible() != null) {
            target.setVisible(source.getVisible());
        }
        if (source.getStatus() != null) {
            target.setStatus(source.getStatus());
        }
    }

    private SysMenu snapshot(SysMenu source) {
        SysMenu copy = new SysMenu();
        copy.setId(source.getId());
        copy.setParentId(source.getParentId());
        copy.setMenuName(source.getMenuName());
        copy.setMenuType(source.getMenuType());
        copy.setPath(source.getPath());
        copy.setComponent(source.getComponent());
        copy.setPerms(source.getPerms());
        copy.setIcon(source.getIcon());
        copy.setOrderNum(source.getOrderNum());
        copy.setVisible(source.getVisible());
        copy.setStatus(source.getStatus());
        return copy;
    }

    /** 供其他模块（如登录菜单树）复用的 id → 菜单映射。 */
    public Map<Long, SysMenu> indexById() {
        Map<Long, SysMenu> map = new HashMap<>();
        for (SysMenu menu : menuMapper.selectList(null)) {
            map.put(menu.getId(), menu);
        }
        return map;
    }
}
