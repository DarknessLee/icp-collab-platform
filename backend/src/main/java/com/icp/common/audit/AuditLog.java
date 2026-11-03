package com.icp.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解：标注在 Controller 方法上，由 AuditLogAspect 写入 sys_oper_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作模块，如「认证」「组织权限」。 */
    String module() default "";

    /** 操作描述，如「登录」「新增用户」。 */
    String action() default "";

    /** 业务类型：login/insert/update/delete/export/other。 */
    String businessType() default "other";
}
