package com.icp.common.datascope;

/**
 * 数据权限上下文（ThreadLocal），由 {@link DataScopeAspect} 在 service 方法执行期间维护。
 */
public final class DataScopeContext {

    private static final ThreadLocal<DataScopeInfo> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(DataScopeInfo info) {
        HOLDER.set(info);
    }

    public static DataScopeInfo get() {
        return HOLDER.get();
    }

    public static DataScopeInfo require() {
        DataScopeInfo info = HOLDER.get();
        if (info == null) {
            throw new IllegalStateException("data scope not initialized; annotate service method with @DataScope");
        }
        return info;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
