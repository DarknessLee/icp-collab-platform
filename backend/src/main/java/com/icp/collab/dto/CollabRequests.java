package com.icp.collab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 协同运营域请求体（M2d：任务/文档/借阅/公告/消息/日程）。
 */
public final class CollabRequests {

    private CollabRequests() {
    }

    public record TaskCreate(
            @NotBlank String taskTitle,
            Integer taskType,
            Integer priority,
            @NotNull Long assigneeId,
            Long deptId,
            LocalDate planStart,
            @NotNull LocalDate planEnd,
            LocalDateTime remindTime,
            String description,
            Long parentId,
            String remark) {
    }

    public record TaskUpdate(
            String taskTitle,
            Integer priority,
            Long assigneeId,
            Long deptId,
            LocalDate planStart,
            LocalDate planEnd,
            LocalDateTime remindTime,
            String description,
            String remark,
            Long parentId) {
    }

    public record CommentAdd(@NotBlank String content) {
    }

    public record DocCreate(
            @NotBlank String docName,
            @NotNull Long categoryId,
            Integer docType,
            Long fileId,
            Long deptId,
            String version,
            String description,
            String remark) {
    }

    public record DocUpdate(
            String docName,
            Long categoryId,
            Integer docType,
            Long fileId,
            Long deptId,
            String description,
            String remark) {
    }

    public record PermItem(
            @NotNull Integer targetType,
            @NotNull Long targetId,
            @NotNull Integer permType) {
    }

    public record DocPermPut(@NotNull List<PermItem> perms) {
    }

    public record BorrowCreate(
            @NotNull Long docId,
            @NotBlank String reason,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record NoticeCreate(
            @NotBlank String noticeTitle,
            Integer noticeType,
            String content,
            Integer scope,
            List<Long> scopeDeptIds,
            List<String> scopeRoles,
            Integer isTop,
            String remark) {
    }

    public record NoticeUpdate(
            String noticeTitle,
            Integer noticeType,
            String content,
            Integer scope,
            List<Long> scopeDeptIds,
            List<String> scopeRoles,
            Integer isTop,
            String remark) {
    }

    public record MessageSend(
            @NotNull Long receiverId,
            String title,
            String content,
            Integer messageType,
            String bizType,
            Long bizId) {
    }

    public record MessageRead(@NotNull List<Long> ids) {
    }

    public record ScheduleCreate(
            @NotBlank String scheduleTitle,
            @NotNull Integer scheduleType,
            @NotNull LocalDateTime startTime,
            LocalDateTime endTime,
            String location,
            List<Long> participantIds,
            Integer remindMinutes,
            Long deptId,
            String description,
            String remark) {
    }

    public record ScheduleUpdate(
            String scheduleTitle,
            Integer scheduleType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String location,
            List<Long> participantIds,
            Integer remindMinutes,
            Long deptId,
            String description,
            String remark) {
    }
}
