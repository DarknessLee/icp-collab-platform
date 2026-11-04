package com.icp.report.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysUserMapper;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * M2e 报表/预警数据范围辅助（D41）：
 * 复用 M2a {@link DataScopeService} 的 DataScopeInfo，对聚合结果按部门/本人做内存过滤。
 * 报表数据量级在种子里很小（百级），内存过滤比动态 SQL 更直观且口径统一。
 */
@Component
public class ReportScope {

    private final DataScopeService dataScopeService;
    private final SysUserMapper userMapper;

    public ReportScope(DataScopeService dataScopeService, SysUserMapper userMapper) {
        this.dataScopeService = dataScopeService;
        this.userMapper = userMapper;
    }

    public DataScopeInfo current() {
        return dataScopeService.current();
    }

    /** summary 缓存 key 的数据范围部分（D39：key 含 userId 与数据范围）。 */
    public String cacheKey(DataScopeInfo info) {
        return info.getUserId() + ":" + info.getScope() + ":" + info.getDeptIds();
    }

    /** 部门 + 负责人维度（制度/风险/缺陷/整改）。本人维度优先 owner，缺 owner 时按本部门兜底。 */
    public boolean visibleByDeptUser(DataScopeInfo info, Long deptId, Long userId) {
        if (info.isFull()) {
            return true;
        }
        if (info.isSelfOnly()) {
            if (userId != null) {
                return userId.equals(info.getUserId());
            }
            return deptId != null && deptId.equals(info.getDeptId());
        }
        return deptId != null && info.getDeptIds().contains(deptId);
    }

    /** 任务维度：部门范围按 dept_id，本人范围按 assignee/creator。 */
    public boolean visibleTask(DataScopeInfo info, Long deptId, Long assigneeId, Long creatorId) {
        if (info.isFull()) {
            return true;
        }
        Long me = info.getUserId();
        if (info.isSelfOnly()) {
            return equalsValue(me, assigneeId) || equalsValue(me, creatorId);
        }
        return (deptId != null && info.getDeptIds().contains(deptId))
                || equalsValue(me, assigneeId) || equalsValue(me, creatorId);
    }

    /** 流程维度：按发起人部门（wf_business_link.initiator_id 关联 sys_user.dept_id）。 */
    public boolean visibleLink(DataScopeInfo info, Long initiatorId) {
        if (info.isFull()) {
            return true;
        }
        if (initiatorId == null) {
            return false;
        }
        if (initiatorId.equals(info.getUserId())) {
            return true;
        }
        if (info.isSelfOnly()) {
            return false;
        }
        return deptUserIds(info.getDeptIds()).contains(initiatorId);
    }

    /**
     * 越权 deptId 查询参数：M2e 选择「忽略」策略（不报 40300）——返回 null 表示不过滤，
     * 返回原值表示按该部门过滤（在范围内时）。
     */
    public Long effectiveDeptFilter(DataScopeInfo info, Long requestedDeptId) {
        if (requestedDeptId == null) {
            return null;
        }
        if (info.isFull()) {
            return requestedDeptId;
        }
        if (info.getDeptIds() != null && info.getDeptIds().contains(requestedDeptId)) {
            return requestedDeptId;
        }
        return null;
    }

    private List<Long> deptUserIds(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId)
                .in(SysUser::getDeptId, deptIds));
        List<Long> ids = new ArrayList<>(users.size());
        for (SysUser user : users) {
            ids.add(user.getId());
        }
        return ids;
    }

    private static boolean equalsValue(Long expected, Long actual) {
        return expected != null && expected.equals(actual);
    }
}
