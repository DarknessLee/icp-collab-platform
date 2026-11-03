package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.dto.Requests;
import com.icp.system.entity.SysConfig;
import com.icp.system.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统参数：列表 + 仅允许修改参数值（config_type=Y 的内置参数拒绝修改）。
 */
@Tag(name = "系统参数")
@RestController
@RequestMapping("/api/v1/configs")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(summary = "参数分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:config:list')")
    public ApiResponse<PageResult<SysConfig>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "100") long pageSize,
            @RequestParam(required = false) String keyword) {
        long size = Math.min(Math.max(pageSize, 1), 100);
        return ApiResponse.ok(configService.page(Math.max(pageNum, 1), size, keyword));
    }

    @Operation(summary = "修改参数值")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:edit')")
    @AuditLog(module = "系统管理", action = "修改参数", businessType = "sys_config")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody Requests.ConfigValue request) {
        configService.updateValue(id, request.configValue());
        return ApiResponse.ok();
    }
}
