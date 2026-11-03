package com.icp.system.config;

import com.icp.common.datascope.DataScope;
import com.icp.common.datascope.DataScopeContext;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.system.service.DataScopeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面：进入 @DataScope 方法前解析当前用户可见范围，放入 ThreadLocal 上下文，
 * 方法结束（含异常）后清理，避免线程复用导致越权。
 */
@Aspect
@Component
public class DataScopeAspect {

    private final DataScopeService dataScopeService;

    public DataScopeAspect(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        DataScopeInfo info = dataScopeService.current();
        DataScopeContext.set(info);
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
