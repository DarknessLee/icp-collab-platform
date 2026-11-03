package com.icp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.support.CodeSeq;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysUserMapper;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.entity.CollDocBorrow;
import com.icp.workflow.entity.WfPurchase;
import com.icp.workflow.entity.WfSeal;
import com.icp.workflow.mapper.CollDocBorrowMapper;
import com.icp.workflow.mapper.WfPurchaseMapper;
import com.icp.workflow.mapper.WfSealMapper;
import com.icp.workflow.support.WorkflowConstants;
import com.icp.workflow.support.WorkflowUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程中心业务单据服务：采购申请 / 用印申请 / 文档借阅（docs/03 §5）。
 *
 * <p>业务单据可先建草稿再发起流程，也可在创建时直接发起；所有列表按数据权限过滤。</p>
 */
@Service
public class WorkflowBusinessService {

    private final WfPurchaseMapper purchaseMapper;
    private final WfSealMapper sealMapper;
    private final CollDocBorrowMapper borrowMapper;
    private final WorkflowInstanceService instanceService;
    private final WorkflowUserResolver userResolver;
    private final DataScopeService dataScopeService;
    private final SysUserMapper sysUserMapper;

    public WorkflowBusinessService(WfPurchaseMapper purchaseMapper,
                                   WfSealMapper sealMapper,
                                   CollDocBorrowMapper borrowMapper,
                                   WorkflowInstanceService instanceService,
                                   WorkflowUserResolver userResolver,
                                   DataScopeService dataScopeService,
                                   SysUserMapper sysUserMapper) {
        this.purchaseMapper = purchaseMapper;
        this.sealMapper = sealMapper;
        this.borrowMapper = borrowMapper;
        this.instanceService = instanceService;
        this.userResolver = userResolver;
        this.dataScopeService = dataScopeService;
        this.sysUserMapper = sysUserMapper;
    }

    // ------------------------------------------------------------------ 采购申请

    public PageResult<Map<String, Object>> purchases(int pageNum, int pageSize, Integer status, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<WfPurchase> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(WfPurchase::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WfPurchase::getTitle, keyword).or().like(WfPurchase::getApplyNo, keyword));
        }
        if (!info.isFull()) {
            if (info.isSelfOnly()) {
                wrapper.eq(WfPurchase::getApplicantId, info.getUserId());
            } else if (info.getDeptIds() != null && !info.getDeptIds().isEmpty()) {
                wrapper.in(WfPurchase::getDeptId, info.getDeptIds());
            }
        }
        wrapper.orderByDesc(WfPurchase::getApplyDate).orderByDesc(WfPurchase::getId);
        long total = purchaseMapper.selectCount(wrapper);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + from);
        List<WfPurchase> rows = purchaseMapper.selectList(wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (WfPurchase row : rows) {
            list.add(purchaseItem(row));
        }
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    public Map<String, Object> purchase(Long id) {
        WfPurchase row = purchaseMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购申请不存在");
        }
        dataScopeService.checkAccess(dataScopeService.current(), row.getDeptId(), row.getApplicantId());
        return purchaseItem(row);
    }

    @Transactional
    public Map<String, Object> createPurchase(WorkflowRequests.PurchaseRequest request) {
        Long userId = userResolver.currentUserId();
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "采购金额必须大于 0");
        }
        if (request.budgetAmount() != null && request.amount().compareTo(request.budgetAmount()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "采购金额不能超过预算金额");
        }
        WfPurchase purchase = new WfPurchase();
        purchase.setApplyNo(CodeSeq.next(purchaseMapper.selectMaxCode(), "CG", 3));
        purchase.setTitle(request.title());
        purchase.setDeptId(request.deptId() == null ? currentDeptId(userId) : request.deptId());
        purchase.setApplicantId(userId);
        purchase.setAmount(request.amount());
        purchase.setBudgetAmount(request.budgetAmount());
        purchase.setSupplier(request.supplier());
        purchase.setPurchaseType(request.purchaseType() == null ? 1 : request.purchaseType());
        purchase.setPurpose(request.purpose());
        purchase.setApplyDate(request.applyDate() == null ? LocalDate.now() : request.applyDate());
        purchase.setStatus(0);
        purchaseMapper.insert(purchase);
        if (Boolean.TRUE.equals(request.submit())) {
            Map<String, Object> started = instanceService.start(new WorkflowRequests.StartRequest(
                    WorkflowConstants.PURCHASE_APPLY, purchase.getId(), "purchase",
                    purchaseFormData(purchase), purchase.getTitle()), null);
            Map<String, Object> data = purchaseItem(purchaseMapper.selectById(purchase.getId()));
            data.put("workflow", started);
            return data;
        }
        return purchaseItem(purchase);
    }

    @Transactional
    public Map<String, Object> submitPurchase(Long id) {
        WfPurchase row = purchaseMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "采购申请不存在");
        }
        Map<String, Object> started = instanceService.start(new WorkflowRequests.StartRequest(
                WorkflowConstants.PURCHASE_APPLY, id, "purchase", purchaseFormData(row), row.getTitle()), null);
        Map<String, Object> data = purchaseItem(purchaseMapper.selectById(id));
        data.put("workflow", started);
        data.put("pendingWorkflow", false);
        return data;
    }

    private Map<String, Object> purchaseFormData(WfPurchase purchase) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("title", purchase.getTitle());
        form.put("applyNo", purchase.getApplyNo());
        form.put("amount", purchase.getAmount());
        form.put("budgetAmount", purchase.getBudgetAmount());
        form.put("supplier", purchase.getSupplier());
        form.put("purchaseType", purchase.getPurchaseType());
        form.put("purpose", purchase.getPurpose());
        form.put("applyDate", purchase.getApplyDate() == null ? null : purchase.getApplyDate().toString());
        form.put("deptId", purchase.getDeptId());
        return form;
    }

    private Map<String, Object> purchaseItem(WfPurchase row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("applyNo", row.getApplyNo());
        item.put("title", row.getTitle());
        item.put("deptId", row.getDeptId());
        item.put("deptName", row.getDeptId() == null ? null : userResolver.deptNames(List.of(row.getDeptId())).get(row.getDeptId()));
        item.put("applicantId", row.getApplicantId());
        item.put("applicantName", userResolver.nameOf(row.getApplicantId()));
        item.put("amount", row.getAmount());
        item.put("budgetAmount", row.getBudgetAmount());
        item.put("supplier", row.getSupplier());
        item.put("purchaseType", row.getPurchaseType());
        item.put("purpose", row.getPurpose());
        item.put("applyDate", row.getApplyDate());
        item.put("status", row.getStatus());
        item.put("flowInstanceId", row.getFlowInstanceId());
        item.put("remark", row.getRemark());
        return item;
    }

    // ------------------------------------------------------------------ 用印申请

    public PageResult<Map<String, Object>> seals(int pageNum, int pageSize, Integer status, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<WfSeal> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(WfSeal::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WfSeal::getReason, keyword).or().like(WfSeal::getApplyNo, keyword));
        }
        if (!info.isFull()) {
            if (info.isSelfOnly()) {
                wrapper.eq(WfSeal::getApplicantId, info.getUserId());
            } else if (info.getDeptIds() != null && !info.getDeptIds().isEmpty()) {
                wrapper.in(WfSeal::getDeptId, info.getDeptIds());
            }
        }
        wrapper.orderByDesc(WfSeal::getUseDate).orderByDesc(WfSeal::getId);
        long total = sealMapper.selectCount(wrapper);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + from);
        List<WfSeal> rows = sealMapper.selectList(wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (WfSeal row : rows) {
            list.add(sealItem(row));
        }
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    public Map<String, Object> seal(Long id) {
        WfSeal row = sealMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用印申请不存在");
        }
        dataScopeService.checkAccess(dataScopeService.current(), row.getDeptId(), row.getApplicantId());
        return sealItem(row);
    }

    @Transactional
    public Map<String, Object> createSeal(WorkflowRequests.SealRequest request) {
        Long userId = userResolver.currentUserId();
        WfSeal seal = new WfSeal();
        seal.setApplyNo(CodeSeq.next(sealMapper.selectMaxCode(), "YY", 3));
        seal.setSealType(request.sealType());
        seal.setReason(request.reason());
        seal.setUseDate(request.useDate() == null ? LocalDate.now() : request.useDate());
        seal.setFileId(request.fileId());
        seal.setApplicantId(userId);
        seal.setDeptId(request.deptId() == null ? currentDeptId(userId) : request.deptId());
        seal.setStatus(0);
        sealMapper.insert(seal);
        if (Boolean.TRUE.equals(request.submit())) {
            Map<String, Object> started = instanceService.start(new WorkflowRequests.StartRequest(
                    WorkflowConstants.SEAL_APPLY, seal.getId(), "seal",
                    sealFormData(seal), seal.getReason()), null);
            Map<String, Object> data = sealItem(sealMapper.selectById(seal.getId()));
            data.put("workflow", started);
            return data;
        }
        return sealItem(seal);
    }

    @Transactional
    public Map<String, Object> submitSeal(Long id) {
        WfSeal row = sealMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用印申请不存在");
        }
        Map<String, Object> started = instanceService.start(new WorkflowRequests.StartRequest(
                WorkflowConstants.SEAL_APPLY, id, "seal", sealFormData(row), row.getReason()), null);
        Map<String, Object> data = sealItem(sealMapper.selectById(id));
        data.put("workflow", started);
        data.put("pendingWorkflow", false);
        return data;
    }

    private Map<String, Object> sealFormData(WfSeal seal) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("applyNo", seal.getApplyNo());
        form.put("reason", seal.getReason());
        form.put("sealType", seal.getSealType());
        form.put("useDate", seal.getUseDate() == null ? null : seal.getUseDate().toString());
        form.put("fileId", seal.getFileId());
        form.put("deptId", seal.getDeptId());
        return form;
    }

    private Map<String, Object> sealItem(WfSeal row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("applyNo", row.getApplyNo());
        item.put("sealType", row.getSealType());
        item.put("reason", row.getReason());
        item.put("useDate", row.getUseDate());
        item.put("fileId", row.getFileId());
        item.put("applicantId", row.getApplicantId());
        item.put("applicantName", userResolver.nameOf(row.getApplicantId()));
        item.put("deptId", row.getDeptId());
        item.put("deptName", row.getDeptId() == null ? null : userResolver.deptNames(List.of(row.getDeptId())).get(row.getDeptId()));
        item.put("status", row.getStatus());
        item.put("flowInstanceId", row.getFlowInstanceId());
        item.put("remark", row.getRemark());
        return item;
    }

    // ------------------------------------------------------------------ 文档借阅

    public PageResult<Map<String, Object>> borrows(int pageNum, int pageSize, Integer status, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<CollDocBorrow> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CollDocBorrow::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(CollDocBorrow::getReason, keyword);
        }
        if (!info.isFull()) {
            if (info.isSelfOnly()) {
                wrapper.eq(CollDocBorrow::getBorrowerId, info.getUserId());
            } else if (info.getDeptIds() != null && !info.getDeptIds().isEmpty()) {
                List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, info.getDeptIds()));
                List<Long> ids = users.stream().map(SysUser::getId).toList();
                if (ids.isEmpty()) {
                    return new PageResult<>(List.of(), 0, pageNum, pageSize);
                }
                wrapper.in(CollDocBorrow::getBorrowerId, ids);
            }
        }
        wrapper.orderByDesc(CollDocBorrow::getStartDate).orderByDesc(CollDocBorrow::getId);
        long total = borrowMapper.selectCount(wrapper);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + from);
        List<CollDocBorrow> rows = borrowMapper.selectList(wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CollDocBorrow row : rows) {
            list.add(borrowItem(row));
        }
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    public Map<String, Object> borrow(Long id) {
        CollDocBorrow row = borrowMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "借阅申请不存在");
        }
        dataScopeService.checkAccess(dataScopeService.current(), null, row.getBorrowerId());
        return borrowItem(row);
    }

    @Transactional
    public Map<String, Object> createBorrow(WorkflowRequests.BorrowRequest request) {
        Long userId = userResolver.currentUserId();
        if (request.docId() == null || borrowMapper.countDoc(request.docId()) == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "借阅文档不存在");
        }
        LocalDate start = request.startDate() == null ? LocalDate.now() : request.startDate();
        LocalDate end = request.endDate() == null ? start.plusDays(7) : request.endDate();
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "借阅结束日期不能早于开始日期");
        }
        CollDocBorrow borrow = new CollDocBorrow();
        borrow.setDocId(request.docId());
        borrow.setBorrowerId(userId);
        borrow.setReason(request.reason());
        borrow.setStartDate(start);
        borrow.setEndDate(end);
        borrow.setStatus(0);
        borrowMapper.insert(borrow);
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("docId", borrow.getDocId());
        form.put("reason", borrow.getReason());
        form.put("startDate", start.toString());
        form.put("endDate", end.toString());
        Map<String, Object> started = instanceService.start(new WorkflowRequests.StartRequest(
                WorkflowConstants.DOC_BORROW, borrow.getId(), "borrow", form,
                "文档借阅-" + borrow.getReason()), null);
        Map<String, Object> data = borrowItem(borrowMapper.selectById(borrow.getId()));
        data.put("workflow", started);
        data.put("processInstanceId", started.get("processInstanceId"));
        data.put("taskId", started.get("taskId"));
        data.put("pendingWorkflow", false);
        return data;
    }

    private Map<String, Object> borrowItem(CollDocBorrow row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("docId", row.getDocId());
        item.put("borrowerId", row.getBorrowerId());
        item.put("borrowerName", userResolver.nameOf(row.getBorrowerId()));
        item.put("reason", row.getReason());
        item.put("startDate", row.getStartDate());
        item.put("endDate", row.getEndDate());
        item.put("status", row.getStatus());
        item.put("flowInstanceId", row.getFlowInstanceId());
        item.put("remark", row.getRemark());
        return item;
    }

    private Long currentDeptId(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }
}
