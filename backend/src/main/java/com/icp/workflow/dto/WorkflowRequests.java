package com.icp.workflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 流程中心请求体（docs/03 §5）。
 */
public final class WorkflowRequests {

    private WorkflowRequests() {
    }

    public record StartRequest(@NotBlank(message = "流程类型不能为空") String processKey,
                               Long businessId,
                               String businessType,
                               Map<String, Object> formData,
                               String title) {
    }

    public record ApproveRequest(String comment,
                                 Map<String, Object> formData,
                                 List<Long> attachmentIds) {
    }

    public record RejectRequest(@NotBlank(message = "驳回意见不能为空") String comment) {
    }

    public record UrgeRequest(String content) {
    }

    public record PurchaseRequest(String title,
                                  Long deptId,
                                  @NotNull(message = "采购金额不能为空") @DecimalMin(value = "0.01", message = "采购金额必须大于 0") BigDecimal amount,
                                  BigDecimal budgetAmount,
                                  String supplier,
                                  Integer purchaseType,
                                  String purpose,
                                  LocalDate applyDate,
                                  Boolean submit) {
    }

    public record SealRequest(@NotNull(message = "用印类型不能为空") Integer sealType,
                              @NotBlank(message = "用印事由不能为空") String reason,
                              LocalDate useDate,
                              Long fileId,
                              Long deptId,
                              Boolean submit) {
    }

    public record BorrowRequest(@NotNull(message = "文档不能为空") Long docId,
                                @NotBlank(message = "借阅原因不能为空") String reason,
                                LocalDate startDate,
                                LocalDate endDate) {
    }
}
