package com.icp.icp.support;

import com.icp.icp.entity.IcpControl;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpPolicyCategory;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.entity.IcpRiskCategory;
import com.icp.icp.mapper.IcpControlMapper;
import com.icp.icp.mapper.IcpPolicyCategoryMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRiskCategoryMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内控域列表展示用的批量名称查询（部门/分类/用户），避免 N+1 查询。
 */
@Service
public class IcpLookupService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final IcpPolicyCategoryMapper policyCategoryMapper;
    private final IcpRiskCategoryMapper riskCategoryMapper;
    private final IcpRiskMapper riskMapper;
    private final IcpControlMapper controlMapper;
    private final IcpPolicyMapper policyMapper;

    public IcpLookupService(SysDeptMapper deptMapper,
                            SysUserMapper userMapper,
                            IcpPolicyCategoryMapper policyCategoryMapper,
                            IcpRiskCategoryMapper riskCategoryMapper,
                            IcpRiskMapper riskMapper,
                            IcpControlMapper controlMapper,
                            IcpPolicyMapper policyMapper) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.policyCategoryMapper = policyCategoryMapper;
        this.riskCategoryMapper = riskCategoryMapper;
        this.riskMapper = riskMapper;
        this.controlMapper = controlMapper;
        this.policyMapper = policyMapper;
    }

    public Map<Long, String> deptNames(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysDept> rows = deptMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (SysDept row : rows) {
            map.put(row.getId(), row.getDeptName());
        }
        return map;
    }

    public Map<Long, String> userNames(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> rows = userMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (SysUser row : rows) {
            map.put(row.getId(), row.getRealName() == null ? row.getUsername() : row.getRealName());
        }
        return map;
    }

    public Map<Long, String> policyCategoryNames(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<IcpPolicyCategory> rows = policyCategoryMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (IcpPolicyCategory row : rows) {
            map.put(row.getId(), row.getCategoryName());
        }
        return map;
    }

    public Map<Long, String> riskCategoryNames(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<IcpRiskCategory> rows = riskCategoryMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (IcpRiskCategory row : rows) {
            map.put(row.getId(), row.getCategoryName());
        }
        return map;
    }

    /** 风险 id → "编码 名称"，用于矩阵列表展示。 */
    public Map<Long, String> riskLabels(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<IcpRisk> rows = riskMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (IcpRisk row : rows) {
            map.put(row.getId(), (row.getRiskCode() == null ? "" : row.getRiskCode() + " ") + row.getRiskName());
        }
        return map;
    }

    /** 控制点 id → "编码 名称"。 */
    public Map<Long, String> controlLabels(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<IcpControl> rows = controlMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (IcpControl row : rows) {
            map.put(row.getId(), (row.getControlCode() == null ? "" : row.getControlCode() + " ") + row.getControlName());
        }
        return map;
    }

    /** 制度 id → "编码 名称"。 */
    public Map<Long, String> policyLabels(Collection<Long> ids) {
        Set<Long> keys = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<IcpPolicy> rows = policyMapper.selectBatchIds(keys);
        Map<Long, String> map = new HashMap<>();
        for (IcpPolicy row : rows) {
            map.put(row.getId(), (row.getPolicyCode() == null ? "" : row.getPolicyCode() + " ") + row.getPolicyName());
        }
        return map;
    }
}
