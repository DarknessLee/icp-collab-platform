package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 数据权限解析单测：管理员全量、最大范围取角色并集、scope=5 降级、部门树展开、单条访问判断。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysDeptMapper deptMapper;

    private DataScopeService dataScopeService;

    @BeforeEach
    void setUp() {
        dataScopeService = new DataScopeService(userMapper, roleMapper, deptMapper);
        SysUser user = new SysUser();
        user.setId(9L);
        user.setDeptId(8L);
        user.setStatus(1);
        lenient().when(userMapper.selectById(9L)).thenReturn(user);
        lenient().when(deptMapper.selectList(any())).thenReturn(depts());
    }

    @Test
    void adminRoleGetsFullScope() {
        when(roleMapper.selectByUserId(9L)).thenReturn(List.of(role(1L, "ADMIN", 1)));
        DataScopeInfo info = dataScopeService.forUser(9L);
        assertThat(info.getScope()).isEqualTo(DataScopeInfo.ALL);
        assertThat(info.isFull()).isTrue();
    }

    @Test
    void maxScopeWinsAcrossRoles() {
        when(roleMapper.selectByUserId(9L)).thenReturn(List.of(
                role(3L, "DEPT_MANAGER", 3),
                role(4L, "EMPLOYEE", 1)));
        DataScopeInfo info = dataScopeService.forUser(9L);
        assertThat(info.getScope()).isEqualTo(DataScopeInfo.DEPT_AND_CHILD);
        assertThat(info.getDeptIds()).containsExactlyInAnyOrder(8L);
    }

    @Test
    void customScopeFiveDegradesToDept() {
        when(roleMapper.selectByUserId(9L)).thenReturn(List.of(role(9L, "CUSTOM", 5)));
        DataScopeInfo info = dataScopeService.forUser(9L);
        assertThat(info.getScope()).isEqualTo(DataScopeInfo.DEPT);
        assertThat(info.getDeptIds()).containsExactly(8L);
    }

    @Test
    void deptAndChildIncludesDescendants() {
        SysUser user = new SysUser();
        user.setId(5L);
        user.setDeptId(2L);
        user.setStatus(1);
        when(userMapper.selectById(5L)).thenReturn(user);
        when(roleMapper.selectByUserId(5L)).thenReturn(List.of(role(3L, "DEPT_MANAGER", 3)));
        DataScopeInfo info = dataScopeService.forUser(5L);
        assertThat(info.getDeptIds()).containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void selfOnlyOnlyAllowsOwnRecords() {
        SysUser user = new SysUser();
        user.setId(11L);
        user.setDeptId(2L);
        user.setStatus(1);
        when(userMapper.selectById(11L)).thenReturn(user);
        when(roleMapper.selectByUserId(11L)).thenReturn(List.of(role(4L, "EMPLOYEE", 1)));
        DataScopeInfo info = dataScopeService.forUser(11L);

        assertThat(info.isSelfOnly()).isTrue();
        assertThat(dataScopeService.canAccess(info, 2L, 11L)).isTrue();
        assertThat(dataScopeService.canAccess(info, 2L, 12L)).isFalse();
        assertThatThrownBy(() -> dataScopeService.checkAccess(info, 2L, 12L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void disabledUserCannotResolveScope() {
        SysUser disabled = new SysUser();
        disabled.setId(20L);
        disabled.setStatus(0);
        when(userMapper.selectById(20L)).thenReturn(disabled);
        assertThatThrownBy(() -> dataScopeService.forUser(20L))
                .isInstanceOf(BusinessException.class);
    }

    private SysRole role(Long id, String code, int scope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setDataScope(scope);
        return role;
    }

    private List<SysDept> depts() {
        List<SysDept> list = new ArrayList<>();
        list.add(dept(1L, 0L));
        list.add(dept(2L, 1L));
        list.add(dept(3L, 2L));
        list.add(dept(4L, 2L));
        list.add(dept(8L, 1L));
        return list;
    }

    private SysDept dept(Long id, Long parentId) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        return dept;
    }
}
