package com.icp.common.api;

/**
 * 业务错误码（M1 冻结子集，后续按 docs/03 扩增）。
 */
public enum ErrorCode {

    SUCCESS(0, "OK"),
    PARAM_ERROR(40000, "参数校验失败"),
    STATE_INVALID(40001, "业务状态不允许"),
    DUPLICATE_SUBMIT(40002, "重复提交"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),
    LOGIN_FAILED(40101, "用户名或密码错误"),
    ACCOUNT_LOCKED(40102, "账号已被锁定，请稍后再试"),
    TOKEN_INVALID(40103, "令牌无效或已过期"),
    FORBIDDEN(40300, "无访问权限"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    FILE_INVALID(41300, "文件过大或类型不允许"),
    FILE_UNSUPPORTED(41301, "该文件类型不支持预览"),
    TOO_FREQUENT(42900, "操作过于频繁"),
    SYSTEM_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 对齐 docs/03 §1.5：除框架级 401/403/500 外，业务失败统一 HTTP 200，由 code 表达业务结果。
     */
    public int httpStatus() {
        switch (this) {
            case UNAUTHORIZED:
            case TOKEN_INVALID:
                return 401;
            case FORBIDDEN:
                return 403;
            case SYSTEM_ERROR:
                return 500;
            default:
                return 200;
        }
    }
}
