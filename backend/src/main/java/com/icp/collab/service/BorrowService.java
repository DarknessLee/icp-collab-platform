package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollDoc;
import com.icp.collab.entity.CollDocBorrow;
import com.icp.collab.mapper.CollBorrowMapper;
import com.icp.collab.mapper.CollDocMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.service.WorkflowInstanceService;
import com.icp.workflow.support.WorkflowConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档借阅（M2d / docs/03 §6.1 rule 6）：创建即发起 doc_borrow 流程；
 * 状态 0申请中 / 1已通过 / 2已驳回 / 3已归还（流程回调推进，M2c onApproved=1）。
 */
@Service
public class BorrowService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_RETURNED = 3;

    private final CollBorrowMapper borrowMapper;
    private final CollDocMapper docMapper;
    private final CollabLookupService lookup;
    private final DataScopeService dataScopeService;
    private final WorkflowInstanceService workflowInstanceService;

    public BorrowService(CollBorrowMapper borrowMapper,
                         CollDocMapper docMapper,
                         CollabLookupService lookup,
                         DataScopeService dataScopeService,
                         WorkflowInstanceService workflowInstanceService) {
        this.borrowMapper = borrowMapper;
        this.docMapper = docMapper;
        this.lookup = lookup;
        this.dataScopeService = dataScopeService;
        this.workflowInstanceService = workflowInstanceService;
    }

    public PageResult<CollDocBorrow> page(long pageNum, long pageSize, Integer status, Long docId, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        Long me = lookup.currentUserId();
        LambdaQueryWrapper<CollDocBorrow> wrapper = new LambdaQueryWrapper<>();
        if (!info.isFull()) {
            if (info.isSelfOnly()) {
                wrapper.eq(CollDocBorrow::getBorrowerId, me);
            } else if (info.isDeptRestricted()) {
                Set<Long> userIds = userIdsOfDepts(info.getDeptIds());
                wrapper.and(w -> w.in(CollDocBorrow::getBorrowerId, userIds)
                        .or().eq(CollDocBorrow::getBorrowerId, me));
            }
        }
        if (status != null) {
            wrapper.eq(CollDocBorrow::getStatus, status);
        }
        if (docId != null) {
            wrapper.eq(CollDocBorrow::getDocId, docId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(CollDocBorrow::getReason, keyword);
        }
        wrapper.orderByDesc(CollDocBorrow::getId);
        Page<CollDocBorrow> page = borrowMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fill(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public CollDocBorrow get(Long id) {
        CollDocBorrow borrow = requireVisible(id);
        fill(List.of(borrow));
        return borrow;
    }

    /** POST /borrows：创建借阅单并启动 doc_borrow 流程（返回 processInstanceId/taskId/businessNo）。 */
    @Transactional
    public Map<String, Object> create(CollabRequests.BorrowCreate request) {
        CollDoc doc = docMapper.selectById(request.docId());
        if (doc == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文档不存在");
        }
        if (Objects.equals(doc.getStatus(), DocService.STATUS_DRAFT)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "草稿文档不可借阅");
        }
        String reason = requireText(request.reason(), "借阅原因", 500);
        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();
        LocalDate endDate = request.endDate() == null ? startDate.plusDays(7) : request.endDate();
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "借阅结束日期不能早于开始日期");
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("docId", doc.getId());
        formData.put("docName", doc.getDocName());
        formData.put("reason", reason);
        formData.put("startDate", startDate.toString());
        formData.put("endDate", endDate.toString());
        Map<String, Object> started = workflowInstanceService.start(
                new WorkflowRequests.StartRequest(WorkflowConstants.DOC_BORROW, null, "borrow", formData,
                        "文档借阅-" + doc.getDocNo()), null);
        Long businessId = started.get("businessId") instanceof Number number ? number.longValue() : null;
        CollDocBorrow borrow = businessId == null ? null : borrowMapper.selectById(businessId);
        if (borrow == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "借阅单创建失败");
        }
        fill(List.of(borrow));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("borrow", borrow);
        result.put("id", borrow.getId());
        result.put("status", borrow.getStatus());
        result.put("pendingWorkflow", false);
        result.put("processInstanceId", started.get("processInstanceId"));
        result.put("taskId", started.get("taskId"));
        result.put("businessNo", started.get("businessNo"));
        return result;
    }

    /** POST /borrows/{id}/return：借阅归还 1→3（仅申请本人或 ADMIN，Lead m05949 约定）。 */
    @Transactional
    public CollDocBorrow returnBook(Long id) {
        CollDocBorrow borrow = requireVisible(id);
        Long me = lookup.currentUserId();
        if (!lookup.isAdmin() && !Objects.equals(borrow.getBorrowerId(), me)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅借阅申请人或管理员可归还");
        }
        if (!Objects.equals(borrow.getStatus(), STATUS_APPROVED)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅已通过的借阅可归还");
        }
        borrow.setStatus(STATUS_RETURNED);
        borrowMapper.updateById(borrow);
        fill(List.of(borrow));
        return borrow;
    }

    // ---------------------------------------------------------------- helpers

    private CollDocBorrow requireVisible(Long id) {
        CollDocBorrow borrow = borrowMapper.selectById(id);
        if (borrow == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "借阅记录不存在");
        }
        DataScopeInfo info = dataScopeService.current();
        Long me = lookup.currentUserId();
        boolean visible = info.isFull() || Objects.equals(borrow.getBorrowerId(), me);
        if (!visible && info.isDeptRestricted() && borrow.getBorrowerId() != null) {
            Long borrowerDept = lookup.userDeptId(borrow.getBorrowerId());
            visible = borrowerDept != null && info.getDeptIds().contains(borrowerDept);
        }
        if (!visible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限访问该记录");
        }
        return borrow;
    }

    private Set<Long> userIdsOfDepts(Collection<Long> deptIds) {
        return lookup.userIdsOfDepts(deptIds);
    }

    private void fill(List<CollDocBorrow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> docIds = rows.stream().map(CollDocBorrow::getDocId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CollDoc> docs = new HashMap<>();
        if (!docIds.isEmpty()) {
            for (CollDoc doc : docMapper.selectBatchIds(docIds)) {
                docs.put(doc.getId(), doc);
            }
        }
        Map<Long, String> borrowerNames = lookup.userNames(rows.stream()
                .map(CollDocBorrow::getBorrowerId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (CollDocBorrow row : rows) {
            CollDoc doc = docs.get(row.getDocId());
            if (doc != null) {
                row.setDocName(doc.getDocName());
                row.setDocNo(doc.getDocNo());
            }
            row.setBorrowerName(borrowerNames.get(row.getBorrowerId()));
        }
    }

    private String requireText(String value, String label, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "不能为空");
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "长度不能超过 " + maxLength);
        }
        return text;
    }
}

