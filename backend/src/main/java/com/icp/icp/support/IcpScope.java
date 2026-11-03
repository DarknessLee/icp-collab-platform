package com.icp.icp.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.system.service.DataScopeService;

/**
 * 内控域数据权限套用工具：M2b 各表按 dept 列 + owner/user 列过滤，规则 1 本人 / 2 本部门 / 3 本部门及下级 / 4 全部。
 *
 * <p>与 M2a 的 system 域一致：多角色取最大范围（ADMIN=4），仅对登录用户生效；单条越权由
 * {@link #check(DataScopeService, DataScopeInfo, Long, Long)} 抛 40300。</p>
 */
public final class IcpScope {

    private IcpScope() {
    }

    public static <T> void apply(LambdaQueryWrapper<T> wrapper, DataScopeInfo info,
                                 SFunction<T, ?> deptColumn, SFunction<T, ?> userColumn) {
        if (info == null || info.isFull()) {
            return;
        }
        if (info.isSelfOnly()) {
            if (userColumn != null) {
                wrapper.eq(userColumn, info.getUserId());
            } else if (deptColumn != null) {
                wrapper.eq(deptColumn, info.getDeptId());
            }
            return;
        }
        if (deptColumn != null && info.getDeptIds() != null && !info.getDeptIds().isEmpty()) {
            wrapper.in(deptColumn, info.getDeptIds());
        }
    }

    public static void check(DataScopeService dataScopeService, DataScopeInfo info, Long deptId, Long userId) {
        if (dataScopeService == null || info == null) {
            return;
        }
        dataScopeService.checkAccess(info, deptId, userId);
    }
}
