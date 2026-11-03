package com.icp.common.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解：标注在 service 方法上，进入方法前解析当前用户数据范围（1本人/2本部门/3本部门及下级/4全部）。
 * 解析结果放入 {@link DataScopeContext}，service 内部据此对查询条件做 SQL 级过滤。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 用途说明（仅文档意义）。 */
    String value() default "";
}
