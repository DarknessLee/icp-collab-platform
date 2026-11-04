package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollDocBorrow;
import com.icp.collab.service.BorrowService;
import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "协同-文档借阅")
@RestController
@RequestMapping("/api/v1/borrows")
public class BorrowController {

    private final BorrowService service;

    public BorrowController(BorrowService service) {
        this.service = service;
    }

    @Operation(summary = "借阅申请分页（本人/本部门数据权限）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:borrow:list')")
    public ApiResponse<PageResult<CollDocBorrow>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, status, docId, keyword));
    }

    @Operation(summary = "借阅申请详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:borrow:get')")
    public ApiResponse<CollDocBorrow> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "提交借阅申请（自动发起 doc_borrow 流程）")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:borrow:add')")
    @AuditLog(module = "协同运营", action = "提交借阅申请", businessType = "coll_doc_borrow")
    public ApiResponse<Map<String, Object>> create(@RequestBody CollabRequests.BorrowCreate request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "归还（1已通过→3已归还）")
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyAuthority('collab:borrow:add','collab:borrow:get','collab:borrow:list')")
    @AuditLog(module = "协同运营", action = "归还文档", businessType = "coll_doc_borrow")
    public ApiResponse<CollDocBorrow> returnBook(@PathVariable Long id) {
        return ApiResponse.ok(service.returnBook(id));
    }
}
