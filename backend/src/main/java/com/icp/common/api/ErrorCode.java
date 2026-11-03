package com.icp.common.api;

/**
 * 业务错误码（M1 冻结子集，后续按 docs/03 扩增）。
 */
public enum ErrorCode {

    SUCCESS(0, "OK"),
    PARAM_ERROR(40000, "参数校验失败"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),
    LOGIN_FAILED(40101, "用户名或密码错误"),
    ACCOUNT_LOCKED(40102, "账号已被锁定，请稍后再试"),
    TOKEN_INVALID(40103, "令牌无效或已过期"),
    FORBIDDEN(40300, "无访问权限"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
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

    public int httpStatus() {
        switch (this) {
            case PARAM_ERROR:
                return 400;
            case UNAUTHORIZED:
            case LOGIN_FAILED:
            case ACCOUNT_LOCKED:
            case TOKEN_INVALID:
                return 401;
            case FORBIDDEN:
                return 403;
            case NOT_FOUND:
                return 404;
            case METHOD_NOT_ALLOWED:
                return 405;
            default:
                return 500;
        }
    }
}
