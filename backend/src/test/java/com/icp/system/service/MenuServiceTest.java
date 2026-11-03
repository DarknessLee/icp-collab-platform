package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysMenu;
import com.icp.system.mapper.SysMenuMapper;
import com.icp.system.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 菜单单测：按钮必须挂在 C 节点、权限标识唯一、删除保护、上级循环校验。
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;
    @Mock
    private ChangeLogService changeLogService;
    @Mock
    private com.icp.common.cache.CacheService cacheService;

    private MenuService menuService;

    @BeforeEach
    void setUp() {
        menuService = new MenuService(menuMapper, roleMenuMapper, changeLogService, cacheService);
    }

    @Test
    void buttonMustBeChildOfCParent() {
        SysMenu request = new SysMenu();
        request.setMenuName("导出");
        request.setMenuType("F");
        request.setParentId(0L);
        request.setPerms("icp:policy:export");

        assertThatThrownBy(() -> menuService.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void duplicatePermsRejected() {
        SysMenu parent = new SysMenu();
        parent.setId(20L);
        parent.setMenuType("C");
        when(menuMapper.selectById(20L)).thenReturn(parent);
        when(menuMapper.selectOne(any())).thenReturn(new SysMenu());

        SysMenu request = new SysMenu();
        request.setMenuName("导出");
        request.setMenuType("F");
        request.setParentId(20L);
        request.setPerms("icp:policy:export");

        assertThatThrownBy(() -> menuService.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void deleteWithChildrenRejected() {
        SysMenu existing = new SysMenu();
        existing.setId(2L);
        when(menuMapper.selectById(2L)).thenReturn(existing);
        when(menuMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> menuService.delete(2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void updateRejectsOwnDescendantAsParent() {
        SysMenu existing = new SysMenu();
        existing.setId(2L);
        existing.setMenuType("M");
        existing.setMenuName("内控管理");
        existing.setPath("/icp");
        SysMenu child = new SysMenu();
        child.setId(20L);
        child.setParentId(2L);
        child.setMenuType("C");
        lenient().when(menuMapper.selectById(2L)).thenReturn(existing);
        lenient().when(menuMapper.selectById(20L)).thenReturn(child);

        SysMenu request = new SysMenu();
        request.setMenuName("内控管理");
        request.setMenuType("M");
        request.setPath("/icp");
        request.setParentId(20L);

        assertThatThrownBy(() -> menuService.update(2L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void treeKeepsChildrenUnderParents() {
        SysMenu dir = new SysMenu();
        dir.setId(2L);
        dir.setParentId(0L);
        dir.setMenuType("M");
        dir.setOrderNum(2);
        SysMenu list = new SysMenu();
        list.setId(20L);
        list.setParentId(2L);
        list.setMenuType("C");
        list.setOrderNum(1);
        when(menuMapper.selectList(any())).thenReturn(java.util.List.of(dir, list));

        var roots = menuService.tree();
        assertThat(roots).hasSize(1);
        assertThat(roots.get(0).getChildren()).hasSize(1);
        assertThat(roots.get(0).getChildren().get(0).getId()).isEqualTo(20L);
    }
}
