package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpPolicyCategory;
import com.icp.icp.mapper.IcpPolicyCategoryMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.service.WorkflowInstanceService;
import com.icp.workflow.support.WorkflowConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 制度库（docs/03 §4 / docs/04 §4 状态机）。
 *
 * <p>状态机：0 草稿 →（submit）1 审批中 →（publish）2 已生效 →（abolish）3 已废止；非法流转统一 40000。
 * M2b 送审/发布只做状态流转，不启动 Flowable（flow_instance_id=null，返回 pendingWorkflow=true），M2c 再接流程。</p>
 */
@Service
public class PolicyService {

    private static final int DRAFT = 0;
    private static final int APPROVING = 1;
    private static final int EFFECTIVE = 2;
    private static final int ABOLISHED = 3;

    private final IcpPolicyMapper policyMapper;
    private final IcpPolicyCategoryMapper categoryMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;
    private final WorkflowInstanceService workflowInstanceService;

    public PolicyService(IcpPolicyMapper policyMapper,
                         IcpPolicyCategoryMapper categoryMapper,
                         IcpLookupService lookupService,
                         DataScopeService dataScopeService,
                         WorkflowInstanceService workflowInstanceService) {
        this.policyMapper = policyMapper;
        this.categoryMapper = categoryMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
        this.workflowInstanceService = workflowInstanceService;
    }

    public PageResult<IcpPolicy> page(long pageNum, long pageSize, String keyword, Long categoryId, Integer status,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpPolicy> wrapper = new LambdaQueryWrapper<IcpPolicy>()
                .orderByDesc(IcpPolicy::getId);
        IcpScope.apply(wrapper, info, IcpPolicy::getOwnerDeptId, IcpPolicy::getOwnerUserId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpPolicy::getPolicyName, kw)
                    .or().like(IcpPolicy::getPolicyCode, kw)
                    .or().like(IcpPolicy::getKeywords, kw));
        }
        if (categoryId != null) {
            wrapper.eq(IcpPolicy::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(IcpPolicy::getStatus, status);
        }
        if (effectiveFrom != null) {
            wrapper.ge(IcpPolicy::getEffectiveDate, effectiveFrom);
        }
        if (effectiveTo != null) {
            wrapper.le(IcpPolicy::getEffectiveDate, effectiveTo);
        }
        Page<IcpPolicy> page = policyMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpPolicy get(Long id) {
        IcpPolicy policy = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), policy.getOwnerDeptId(), policy.getOwnerUserId());
        fillNames(List.of(policy));
        return policy;
    }

    @Transactional
    public IcpPolicy create(IcpPolicy request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        IcpPolicy policy = new IcpPolicy();
        policy.setPolicyName(request.getPolicyName() == null ? null : request.getPolicyName().trim());
        policy.setCategoryId(request.getCategoryId());
        policy.setVersion(StringUtils.hasText(request.getVersion()) ? request.getVersion().trim() : "V1.0");
        policy.setEffectiveDate(request.getEffectiveDate());
        policy.setExpireDate(request.getExpireDate());
        policy.setContent(request.getContent());
        policy.setKeywords(request.getKeywords());
        policy.setRemark(request.getRemark());
        policy.setStatus(DRAFT);
        policy.setFlowInstanceId(null);
        if (request.getOwnerDeptId() != null) {
            policy.setOwnerDeptId(request.getOwnerDeptId());
        } else {
            policy.setOwnerDeptId(dataScopeService.current().getDeptId());
        }
        policy.setOwnerUserId(request.getOwnerUserId() != null
                ? request.getOwnerUserId() : SecurityUtils.currentUserIdOrNull());
        policy.setPolicyCode(StringUtils.hasText(request.getPolicyCode())
                ? request.getPolicyCode().trim() : CodeSeq.next(policyMapper.selectMaxCode(), "ZD", 4));
        validate(policy);
        policyMapper.insert(policy);
        return policy;
    }

    @Transactional
    public IcpPolicy update(Long id, IcpPolicy request) {
        IcpPolicy existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() != DRAFT) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的制度可编辑");
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "制度状态请使用送审/发布/废止接口变更");
        }
        if (StringUtils.hasText(request.getPolicyName())) {
            existing.setPolicyName(request.getPolicyName().trim());
        }
        if (request.getCategoryId() != null) {
            existing.setCategoryId(request.getCategoryId());
        }
        if (StringUtils.hasText(request.getVersion())) {
            existing.setVersion(request.getVersion().trim());
        }
        if (request.getEffectiveDate() != null) {
            existing.setEffectiveDate(request.getEffectiveDate());
        }
        if (request.getExpireDate() != null) {
            existing.setExpireDate(request.getExpireDate());
        }
        if (request.getContent() != null) {
            existing.setContent(request.getContent());
        }
        if (request.getKeywords() != null) {
            existing.setKeywords(request.getKeywords());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        policyMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        IcpPolicy existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != DRAFT) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的制度可删除");
        }
        if (policyMapper.countMatrixRefs(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "制度已被风险矩阵引用，不能删除");
        }
        policyMapper.deleteById(id);
    }

    /** 制度 0 → 1：真实发起 Flowable 制度发布流程（docs/03 §5.1 D34）。 */
    @Transactional
    public Map<String, Object> submit(Long id) {
        IcpPolicy existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != DRAFT) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的制度可提交");
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("policyCode", existing.getPolicyCode());
        formData.put("policyName", existing.getPolicyName());
        formData.put("categoryId", existing.getCategoryId());
        formData.put("version", existing.getVersion());
        formData.put("effectiveDate", existing.getEffectiveDate() == null ? null : existing.getEffectiveDate().toString());
        formData.put("expireDate", existing.getExpireDate() == null ? null : existing.getExpireDate().toString());
        formData.put("ownerDeptId", existing.getOwnerDeptId());
        Map<String, Object> started = workflowInstanceService.start(new WorkflowRequests.StartRequest(
                WorkflowConstants.POLICY_PUBLISH, id, "policy", formData, existing.getPolicyName()), null);
        IcpPolicy updated = policyMapper.selectById(id);
        Map<String, Object> result = actionResult(updated, false);
        result.put("processInstanceId", started.get("processInstanceId"));
        result.put("taskId", started.get("taskId"));
        result.put("businessNo", started.get("businessNo"));
        return result;
    }

    /** 发布：1 → 2，要求生效日期与发布时间（docs/04 §4）。 */
    @Transactional
    public Map<String, Object> publish(Long id, IcpRequests.Publish request) {
        IcpPolicy existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != APPROVING) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅审批中状态的制度可发布");
        }
        if (request != null && request.effectiveDate() != null) {
            existing.setEffectiveDate(request.effectiveDate());
        }
        if (request != null && request.expireDate() != null) {
            existing.setExpireDate(request.expireDate());
        }
        if (existing.getEffectiveDate() == null) {
            existing.setEffectiveDate(LocalDate.now());
        }
        existing.setStatus(EFFECTIVE);
        existing.setPublishTime(LocalDateTime.now());
        existing.setFlowInstanceId(null);
        validate(existing);
        policyMapper.updateById(existing);
        return actionResult(existing, true);
    }

    /** 废止：2 → 3。 */
    @Transactional
    public Map<String, Object> abolish(Long id) {
        IcpPolicy existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != EFFECTIVE) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅已生效状态的制度可废止");
        }
        existing.setStatus(ABOLISHED);
        policyMapper.updateById(existing);
        return actionResult(existing, true);
    }

    private Map<String, Object> actionResult(IcpPolicy policy, boolean pendingWorkflow) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", policy.getId());
        data.put("policyCode", policy.getPolicyCode());
        data.put("status", policy.getStatus());
        data.put("flowInstanceId", policy.getFlowInstanceId());
        data.put("pendingWorkflow", pendingWorkflow);
        data.put("publishTime", policy.getPublishTime());
        data.put("effectiveDate", policy.getEffectiveDate());
        return data;
    }

    private IcpPolicy require(Long id) {
        IcpPolicy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "制度不存在");
        }
        return policy;
    }

    private void validate(IcpPolicy policy) {
        if (!StringUtils.hasText(policy.getPolicyName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "制度名称不能为空");
        }
        if (policy.getPolicyName().length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "制度名称不能超过 200 个字符");
        }
        if (policy.getCategoryId() != null) {
            IcpPolicyCategory category = categoryMapper.selectById(policy.getCategoryId());
            if (category == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "制度分类不存在");
            }
        }
        if (policy.getEffectiveDate() != null && policy.getExpireDate() != null
                && policy.getEffectiveDate().isAfter(policy.getExpireDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生效日期不能晚于失效日期");
        }
        if (policy.getStatus() != null && policy.getStatus() == EFFECTIVE) {
            if (policy.getEffectiveDate() == null || policy.getPublishTime() == null) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "已生效制度必须同时具备生效日期与发布时间");
            }
        }
    }

    private void fillNames(List<IcpPolicy> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = records.stream().map(IcpPolicy::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> deptIds = records.stream().map(IcpPolicy::getOwnerDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categories = lookupService.policyCategoryNames(categoryIds);
        Map<Long, String> depts = lookupService.deptNames(deptIds);
        for (IcpPolicy record : records) {
            record.setCategoryName(categories.get(record.getCategoryId()));
            record.setOwnerDeptName(depts.get(record.getOwnerDeptId()));
        }
    }

    /** 供报表/前端下拉使用的有效制度（只读，无数据权限过滤）。 */
    public List<IcpPolicy> effectivePolicies() {
        List<IcpPolicy> list = policyMapper.selectList(new LambdaQueryWrapper<IcpPolicy>()
                .eq(IcpPolicy::getStatus, EFFECTIVE)
                .orderByDesc(IcpPolicy::getPublishTime));
        fillNames(list);
        return list;
    }
}
