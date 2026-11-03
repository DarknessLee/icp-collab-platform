package com.icp.common.datascope;

import java.util.Collections;
import java.util.List;

/**
 * 当前用户数据范围快照。
 * scope: 1 本人 / 2 本部门 / 3 本部门及下级 / 4 全部（5 自定义本期不启用）。
 */
public class DataScopeInfo {

    public static final int SELF = 1;
    public static final int DEPT = 2;
    public static final int DEPT_AND_CHILD = 3;
    public static final int ALL = 4;

    private final Long userId;
    private final Long deptId;
    private final int scope;
    private final List<Long> deptIds;

    public DataScopeInfo(Long userId, Long deptId, int scope, List<Long> deptIds) {
        this.userId = userId;
        this.deptId = deptId;
        this.scope = scope;
        this.deptIds = deptIds == null ? Collections.emptyList() : deptIds;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public int getScope() {
        return scope;
    }

    public List<Long> getDeptIds() {
        return deptIds;
    }

    /** 全量可见（不追加过滤条件）。 */
    public boolean isFull() {
        return scope >= ALL;
    }

    /** 仅本人数据。 */
    public boolean isSelfOnly() {
        return scope == SELF || deptIds.isEmpty();
    }

    /** 部门范围（scope 2/3 且部门集合非空）。 */
    public boolean isDeptRestricted() {
        return !isFull() && !isSelfOnly();
    }
}
