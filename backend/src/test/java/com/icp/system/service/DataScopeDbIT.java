package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据权限真实库集成测试（6 角色对种子用户的可见行数）。
 * <p>类名以 IT 结尾，默认 surefire 不执行；本地 MySQL 3307 / 库 icp_platform 就绪时手动运行：</p>
 * <pre>
 *   mvn -B -Dtest=DataScopeDbIT test
 * </pre>
 * 预期：admin/icpadmin/auditor/exec = 全部 30 人；dept01(部门负责人, dept4) = 4 人；staff01(本人) = 1 人。
 */
@SpringBootTest
@ActiveProfiles("local")
class DataScopeDbIT {

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private SysUserMapper userMapper;

    @Test
    void sixRolesSeeExpectedRowsAgainstSeed() {
        assertScope(1L, 4, 30);
        assertScope(3L, 4, 30);
        assertScope(4L, 4, 30);
        assertScope(2L, 4, 30);
        assertScope(5L, 3, 4);
        assertScope(11L, 1, 1);
    }

    @Test
    void deptManagerOnlySeesOwnDepartment() {
        DataScopeInfo info = dataScopeService.forUser(5L);
        assertThat(info.getDeptIds()).containsExactly(4L);
        assertThat(dataScopeService.canAccess(info, 4L, 11L)).isTrue();
        assertThat(dataScopeService.canAccess(info, 2L, 11L)).isFalse();
    }

    private void assertScope(Long userId, int expectedScope, long expectedRows) {
        DataScopeInfo info = dataScopeService.forUser(userId);
        assertThat(info.getScope()).as("user %s scope", userId).isEqualTo(expectedScope);
        assertThat(visibleRows(info)).as("user %s visible rows", userId).isEqualTo(expectedRows);
    }

    private long visibleRows(DataScopeInfo info) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (!info.isFull()) {
            if (info.isSelfOnly()) {
                wrapper.eq(SysUser::getId, info.getUserId());
            } else {
                wrapper.in(SysUser::getDeptId, info.getDeptIds());
            }
        }
        return userMapper.selectCount(wrapper);
    }
}
