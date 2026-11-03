package com.icp.common.api;

import org.slf4j.MDC;

/**
 * 统一响应体：{code, message, data, traceId}。成功 code=0。
 */
public class ApiResponse<T> {

    public static final String TRACE_ID_KEY = "traceId";
    public static final int SUCCESS_CODE = 0;

    private int code;
    private String message;
    private T data;
    private String traceId;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "OK", data, currentTraceId());
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, currentTraceId());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, currentTraceId());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, currentTraceId());
    }

    public static <T> ApiResponse<T> fail(int code, String message, Object data) {
        @SuppressWarnings("unchecked")
        T typed = (T) data;
        return new ApiResponse<>(code, message, typed, currentTraceId());
    }

    private static String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
