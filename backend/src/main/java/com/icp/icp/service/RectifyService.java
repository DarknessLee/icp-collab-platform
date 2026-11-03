package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 整改跟踪（docs/03 §4 / docs/04 §4）：0 待整改 →1 待复核 →2 已通过 →4 已关闭，3 已驳回。
 *
 * <p>整改通过（approve）会把关联缺陷从整改中(1)推进到待复核(2)，随后缺陷方可关闭（requires approved rectify）。</p>
 */
@Service
public class RectifyService {

    private static final int PENDING = 0;
    private static final int REVIEWING = 1;
    private static final int APPROVED = 2;
    private static final int REJECTED = 3;
    private static final int CLOSED = 4;

    private final IcpRectifyMapper rectifyMapper;
    private final IcpDefectMapper defectMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;

    public RectifyService(IcpRectifyMapper rectifyMapper,
                          IcpDefectMapper defectMapper,
                          IcpLookupService lookupService,
                          DataScopeService dataScopeService) {
        this.rectifyMapper = rectifyMapper;
        this.defectMapper = defectMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<IcpRectify> page(long pageNum, long pageSize, Long defectId, Long deptId, Integer status,
                                       String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpRectify> wrapper = new LambdaQueryWrapper<IcpRectify>().orderByDesc(IcpRectify::getId);
        IcpScope.apply(wrapper, info, IcpRectify::getDeptId, IcpRectify::getResponsibleUserId);
        if (defectId != null) {
            wrapper.eq(IcpRectify::getDefectId, defectId);
        }
        if (deptId != null) {
            wrapper.eq(IcpRectify::getDeptId, deptId);
        }
        if (status != null) {
            wrapper.eq(IcpRectify::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpRectify::getRectifyCode, kw)
                    .or().like(IcpRectify::getRectifyPlan, kw));
        }
        Page<IcpRectify> page = rectifyMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpRectify get(Long id) {
        IcpRectify rectify = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), rectify.getDeptId(), rectify.getResponsibleUserId());
        fillNames(List.of(rectify));
        return rectify;
    }

    @Transactional
    public IcpRectify update(Long id, IcpRectify request) {
        IcpRectify existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getResponsibleUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() != PENDING && existing.getStatus() != REJECTED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待整改或已驳回的整改记录可编辑");
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "整改状态请使用提交/审核/关闭接口变更");
        }
        if (StringUtils.hasText(request.getRectifyPlan())) {
            existing.setRectifyPlan(request.getRectifyPlan());
        }
        if (request.getResponsibleUserId() != null) {
            existing.setResponsibleUserId(request.getResponsibleUserId());
        }
        if (request.getDeadline() != null) {
            existing.setDeadline(request.getDeadline());
        }
        if (request.getEvidence() != null) {
            existing.setEvidence(request.getEvidence());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        rectifyMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    /** 提交整改：0/3 → 1，要求整改证据（docs/04 §4）。 */
    @Transactional
    public IcpRectify submit(Long id, IcpRequests.RectifySubmit request) {
        IcpRectify existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getResponsibleUserId());
        if (existing.getStatus() != PENDING && existing.getStatus() != REJECTED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待整改或已驳回的整改记录可提交");
        }
        String evidence = request == null ? null : request.evidence();
        if (!StringUtils.hasText(evidence) && !StringUtils.hasText(existing.getEvidence())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "整改证据不能为空");
        }
        if (StringUtils.hasText(evidence)) {
            existing.setEvidence(evidence);
        }
        if (existing.getFinishDate() == null) {
            existing.setFinishDate(LocalDate.now());
        }
        existing.setStatus(REVIEWING);
        rectifyMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    /** 审核通过：1 → 2，记录复核人/时间/意见，并把关联缺陷推进到待复核。 */
    @Transactional
    public IcpRectify approve(Long id, IcpRequests.RectifyReview request) {
        IcpRectify existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getResponsibleUserId());
        if (existing.getStatus() != REVIEWING) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待复核状态的整改记录可审核通过");
        }
        existing.setStatus(APPROVED);
        existing.setReviewUserId(SecurityUtils.currentUserIdOrNull());
        existing.setReviewTime(LocalDateTime.now());
        existing.setReviewOpinion(request == null ? null
                : (StringUtils.hasText(request.reviewOpinion()) ? request.reviewOpinion() : "同意"));
        rectifyMapper.updateById(existing);
        advanceDefectToReview(existing.getDefectId());
        fillNames(List.of(existing));
        return existing;
    }

    /** 审核驳回：1 → 3，需填写复核意见。 */
    @Transactional
    public IcpRectify reject(Long id, IcpRequests.RectifyReview request) {
        IcpRectify existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getResponsibleUserId());
        if (existing.getStatus() != REVIEWING) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待复核状态的整改记录可驳回");
        }
        if (request == null || !StringUtils.hasText(request.reviewOpinion())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "驳回时必须填写复核意见");
        }
        existing.setStatus(REJECTED);
        existing.setReviewUserId(SecurityUtils.currentUserIdOrNull());
        existing.setReviewTime(LocalDateTime.now());
        existing.setReviewOpinion(request.reviewOpinion());
        rectifyMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    /** 关闭：2 → 4。 */
    @Transactional
    public IcpRectify close(Long id) {
        IcpRectify existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getResponsibleUserId());
        if (existing.getStatus() != APPROVED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅整改通过的记录可关闭");
        }
        existing.setStatus(CLOSED);
        rectifyMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    private void advanceDefectToReview(Long defectId) {
        if (defectId == null) {
            return;
        }
        IcpDefect defect = defectMapper.selectById(defectId);
        if (defect != null && defect.getStatus() != null && defect.getStatus() == 1) {
            defect.setStatus(2);
            defectMapper.updateById(defect);
        }
    }

    private IcpRectify require(Long id) {
        IcpRectify rectify = rectifyMapper.selectById(id);
        if (rectify == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "整改记录不存在");
        }
        return rectify;
    }

    private void validate(IcpRectify rectify) {
        if (rectify.getDeadline() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "整改期限不能为空");
        }
        if (StringUtils.hasText(rectify.getRectifyPlan()) && rectify.getRectifyPlan().length() > 1000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "整改计划不能超过 1000 个字符");
        }
        if (StringUtils.hasText(rectify.getEvidence()) && rectify.getEvidence().length() > 500) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "整改证据过多");
        }
    }

    private void fillNames(List<IcpRectify> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> deptIds = records.stream().map(IcpRectify::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> defectIds = records.stream().map(IcpRectify::getDefectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = lookupService.deptNames(deptIds);
        Map<Long, IcpDefect> defects = new java.util.HashMap<>();
        if (!defectIds.isEmpty()) {
            for (IcpDefect defect : defectMapper.selectBatchIds(defectIds)) {
                defects.put(defect.getId(), defect);
            }
        }
        for (IcpRectify record : records) {
            record.setDeptName(depts.get(record.getDeptId()));
            IcpDefect defect = defects.get(record.getDefectId());
            if (defect != null) {
                record.setDefectCode(defect.getDefectCode());
                record.setDefectName(defect.getDefectName());
            }
        }
    }
}
