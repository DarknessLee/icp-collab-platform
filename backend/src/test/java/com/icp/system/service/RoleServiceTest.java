package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysMenu;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysRoleMenu;
import com.icp.system.mapper.SysMenuMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysRoleMenuMapper;
import com.icp.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色单测：内置角色保护、数据范围 5 拒绝、菜单整树覆盖补齐祖先。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private ChangeLogService changeLogService;
    @Mock
    private com.icp.common.cache.CacheService cacheService;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleMapper, roleMenuMapper, userRoleMapper, menuMapper,
                changeLogService, cacheService);
    }

    @Test
    void builtInRoleCannotBeDeleted() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", 4));
        lenient().when(roleMenuMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(userRoleMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> roleService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void builtInRoleCodeCannotBeChanged() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", 4));
        lenient().when(roleMenuMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(userRoleMapper.selectCount(any())).thenReturn(0L);

        SysRole request = role(1L, "ROOT", 4);
        assertThatThrownBy(() -> roleService.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void dataScopeFiveIsRejected() {
        SysRole request = new SysRole();
        request.setRoleCode("CUSTOM");
        request.setRoleName("自定义");
        request.setDataScope(5);

        assertThatThrownBy(() -> roleService.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void updateMenusExpandsAncestors() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ICP_MANAGER", 4));
        lenient().when(roleMenuMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(2L, 0L, "M"),
                menu(20L, 2L, "C"),
                menu(108L, 20L, "F")));

        roleService.updateMenus(2L, List.of(108L));

        ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
        verify(roleMenuMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(SysRoleMenu::getMenuId)
                .containsExactlyInAnyOrder(2L, 20L, 108L);
    }

    @Test
    void updateMenusEvictsAuthMenuCache() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ICP_MANAGER", 4));
        lenient().when(roleMenuMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(2L, 0L, "M")));

        roleService.updateMenus(2L, List.of(2L));

        verify(cacheService).evictByPrefix("auth:menus:");
    }

    @Test
    void updateMenusRejectsUnknownMenu() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "ICP_MANAGER", 4));
        lenient().when(roleMenuMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(userRoleMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(2L, 0L, "M")));

        assertThatThrownBy(() -> roleService.updateMenus(2L, List.of(9999L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createRevivesDeletedRoleAndClearsOldMenus() {
        SysRole request = new SysRole();
        request.setRoleCode("verify_scope_role");
        request.setRoleName("VerifyScopeRole");
        request.setDataScope(2);
        request.setStatus(1);

        SysRole deleted = role(8L, "VERIFY_SCOPE_ROLE", 2);
        when(roleMapper.selectOne(any())).thenReturn(null);
        when(roleMapper.selectByCodeIncludeDeleted("VERIFY_SCOPE_ROLE")).thenReturn(deleted);
        lenient().when(roleMapper.selectById(8L)).thenReturn(role(8L, "VERIFY_SCOPE_ROLE", 2));
        lenient().when(roleMenuMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(userRoleMapper.selectCount(any())).thenReturn(0L);

        SysRole created = roleService.create(request);

        assertThat(created.getId()).isEqualTo(8L);
        verify(roleMapper).reviveRole(any(SysRole.class), any());
        verify(roleMapper, org.mockito.Mockito.never()).insert(any(SysRole.class));
        verify(roleMenuMapper).delete(any());
        verify(cacheService).evictByPrefix("auth:menus:");
    }

    private SysRole role(Long id, String code, int dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setDataScope(dataScope);
        role.setStatus(1);
        return role;
    }

    private SysMenu menu(Long id, Long parentId, String type) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuType(type);
        return menu;
    }
}
