package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.system.entity.SysLoginLog;
import com.icp.system.entity.SysOperLog;
import com.icp.system.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日志查询：操作日志、登录日志、变更日志与 trace 追踪。
 * 审计员（AUDITOR）拥有 system:log:* 与 audit:log:view，只读。
 */
@Tag(name = "日志审计")
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @Operation(summary = "操作日志分页")
    @GetMapping("/oper")
    @PreAuthorize("hasAuthority('system:log:oper')")
    public ApiResponse<PageResult<SysOperLog>> pageOper(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String operName,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(logService.pageOper(Math.max(pageNum, 1), clamp(pageSize),
                operName, module, status, startDate, endDate));
    }

    @Operation(summary = "登录日志分页")
    @GetMapping("/login")
    @PreAuthorize("hasAuthority('system:log:login')")
    public ApiResponse<PageResult<SysLoginLog>> pageLogin(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(logService.pageLogin(Math.max(pageNum, 1), clamp(pageSize),
                username, status, startDate, endDate));
    }

    @Operation(summary = "变更日志分页（sys_oper_log title=CHANGE）")
    @GetMapping("/changes")
    @PreAuthorize("hasAuthority('system:log:change')")
    public ApiResponse<PageResult<SysOperLog>> pageChanges(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return ApiResponse.ok(logService.pageChanges(Math.max(pageNum, 1), clamp(pageSize),
                bizType, bizId, startDate, endDate));
    }

    @Operation(summary = "按 traceId 追踪请求日志")
    @GetMapping("/trace/{traceId}")
    @PreAuthorize("hasAnyAuthority('system:log:oper','audit:log:view')")
    public ApiResponse<List<SysOperLog>> trace(@PathVariable String traceId) {
        return ApiResponse.ok(logService.trace(traceId));
    }

    private long clamp(long pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
