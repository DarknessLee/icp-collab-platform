package com.icp.icp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内控域数据权限真实库集成测试（复用 M2a DataScopeService，按 dept 列 + owner/user 列过滤）。
 * <p>类名以 IT 结尾，默认 surefire 不执行；本地 MySQL 3307 / 库 icp_platform 就绪时手动运行：</p>
 * <pre>
 *   mvn -B -Dtest=IcpDataScopeDbIT test
 * </pre>
 * 种子预期：制度 40 条（dept01=6 / staff01=2）；缺陷 30 条（dept01=4 / staff01=2）；风险 40 条（dept01=6）。
 */
@SpringBootTest
@ActiveProfiles("local")
class IcpDataScopeDbIT {

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private IcpPolicyMapper policyMapper;

    @Autowired
    private IcpDefectMapper defectMapper;

    @Autowired
    private IcpRiskMapper riskMapper;

    @Test
    void adminSeesAllIcpRows() {
        DataScopeInfo admin = dataScopeService.forUser(1L);
        assertThat(admin.getScope()).isEqualTo(DataScopeInfo.ALL);
        assertThat(policyRows(admin)).isEqualTo(40);
        assertThat(defectRows(admin)).isEqualTo(30);
        assertThat(riskRows(admin)).isEqualTo(40);
    }

    @Test
    void deptManagerSeesOwnDepartmentOnly() {
        DataScopeInfo manager = dataScopeService.forUser(5L);
        assertThat(manager.getScope()).isEqualTo(DataScopeInfo.DEPT_AND_CHILD);
        assertThat(manager.getDeptIds()).containsExactly(4L);

        assertThat(policyRows(manager)).isEqualTo(6);
        assertThat(defectRows(manager)).isEqualTo(4);
        assertThat(riskRows(manager)).isEqualTo(6);
    }

    @Test
    void employeeSeesOnlyOwnRows() {
        DataScopeInfo employee = dataScopeService.forUser(11L);
        assertThat(employee.getScope()).isEqualTo(DataScopeInfo.SELF);

        assertThat(policyRows(employee)).isEqualTo(2);
        assertThat(defectRows(employee)).isEqualTo(2);
        assertThat(riskRows(employee)).isEqualTo(2);
    }

    @Test
    void outOfScopeSingleRowRejected() {
        DataScopeInfo employee = dataScopeService.forUser(11L);
        IcpPolicy foreign = policyMapper.selectOne(new LambdaQueryWrapper<IcpPolicy>()
                .eq(IcpPolicy::getOwnerDeptId, 6L)
                .last("limit 1"));
        assertThat(foreign).isNotNull();

        assertThatThrownBy(() -> IcpScope.check(dataScopeService, employee,
                foreign.getOwnerDeptId(), foreign.getOwnerUserId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据权限");
    }

    private long policyRows(DataScopeInfo info) {
        LambdaQueryWrapper<IcpPolicy> wrapper = new LambdaQueryWrapper<>();
        IcpScope.apply(wrapper, info, IcpPolicy::getOwnerDeptId, IcpPolicy::getOwnerUserId);
        return policyMapper.selectCount(wrapper);
    }

    private long defectRows(DataScopeInfo info) {
        LambdaQueryWrapper<IcpDefect> wrapper = new LambdaQueryWrapper<>();
        IcpScope.apply(wrapper, info, IcpDefect::getDeptId, IcpDefect::getOwnerUserId);
        return defectMapper.selectCount(wrapper);
    }

    private long riskRows(DataScopeInfo info) {
        LambdaQueryWrapper<IcpRisk> wrapper = new LambdaQueryWrapper<>();
        IcpScope.apply(wrapper, info, IcpRisk::getOwnerDeptId, IcpRisk::getOwnerUserId);
        return riskMapper.selectCount(wrapper);
    }
}
