package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.entity.SysUserRole;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysPostMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import com.icp.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户单测：用户名唯一校验、逻辑删除行恢复（复用旧 id 且整体覆盖请求字段与角色）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysDeptMapper deptMapper;
    @Mock
    private SysPostMapper postMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyService passwordPolicyService;
    @Mock
    private OnlineSessionService onlineSessionService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ChangeLogService changeLogService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, deptMapper, postMapper, roleMapper, userRoleMapper,
                passwordEncoder, passwordPolicyService, onlineSessionService, dataScopeService, changeLogService);
    }

    @Test
    void createRevivesDeletedRowAndAppliesRequestFieldsAndRoles() {
        String username = "scope_verify_u";
        SysUser deleted = new SysUser();
        deleted.setId(32L);
        deleted.setUsername(username);

        SysDept dept = new SysDept();
        dept.setId(5L);
        dept.setDeptName("采购部");

        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("DEPT_MANAGER");
        role.setRoleName("部门负责人");

        lenient().when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectByUsernameIncludeDeleted(username)).thenReturn(deleted);
        when(passwordEncoder.encode("Icp@2026")).thenReturn("ENC");
        when(deptMapper.selectById(5L)).thenReturn(dept);
        when(roleMapper.selectBatchIds(any())).thenReturn(new ArrayList<>(List.of(role)));
        when(userMapper.selectById(32L)).thenReturn(user(32L, username, 5L));
        when(deptMapper.selectBatchIds(any())).thenReturn(new ArrayList<>(List.of(dept)));
        when(roleMapper.selectByUserId(32L)).thenReturn(new ArrayList<>(List.of(role)));

        SysUser request = new SysUser();
        request.setUsername(username);
        request.setRealName("New Name");
        request.setDeptId(5L);
        request.setPassword("Icp@2026");
        request.setStatus(1);
        request.setRoleIds(new ArrayList<>(List.of(3L)));

        SysUser created = userService.create(request);

        ArgumentCaptor<SysUser> reviveCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).reviveUser(reviveCaptor.capture(), any());
        SysUser revived = reviveCaptor.getValue();
        assertThat(revived.getId()).isEqualTo(32L);
        assertThat(revived.getRealName()).isEqualTo("New Name");
        assertThat(revived.getDeptId()).isEqualTo(5L);
        assertThat(revived.getPassword()).isEqualTo("ENC");
        assertThat(revived.getStatus()).isEqualTo(1);

        ArgumentCaptor<SysUserRole> roleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getUserId()).isEqualTo(32L);
        assertThat(roleCaptor.getValue().getRoleId()).isEqualTo(3L);

        verify(changeLogService).record(eq("sys_user"), eq(32L), any(), any());
        assertThat(created.getId()).isEqualTo(32L);
        assertThat(created.getRoleIds()).containsExactly(3L);
    }

    @Test
    void createRejectsActiveDuplicateUsername() {
        SysUser active = user(99L, "admin", 8L);
        when(userMapper.selectOne(any())).thenReturn(active);

        SysUser request = new SysUser();
        request.setUsername("admin");
        request.setRealName("重复用户");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    private SysUser user(Long id, String username, Long deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealName("tester");
        user.setDeptId(deptId);
        user.setStatus(1);
        return user;
    }
}
