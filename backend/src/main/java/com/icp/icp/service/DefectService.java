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
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 缺陷管理（docs/03 §4 / docs/04 §4）：0 已登记 →1 整改中 →2 待复核 →3 已关闭。
 *
 * <p>关闭前置：存在状态为 2（整改通过）的整改记录；缺陷表无 rectify_id 列，关联关系由 icp_rectify.defect_id 反向查询。</p>
 */
@Service
public class DefectService {

    private static final int REGISTERED = 0;
    private static final int RECTIFYING = 1;
    private static final int REVIEWING = 2;
    private static final int CLOSED = 3;

    private final IcpDefectMapper defectMapper;
    private final IcpRectifyMapper rectifyMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;

    public DefectService(IcpDefectMapper defectMapper,
                         IcpRectifyMapper rectifyMapper,
                         IcpLookupService lookupService,
                         DataScopeService dataScopeService) {
        this.defectMapper = defectMapper;
        this.rectifyMapper = rectifyMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<IcpDefect> page(long pageNum, long pageSize, Integer severity, Long deptId, Integer status,
                                      Integer sourceType, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpDefect> wrapper = new LambdaQueryWrapper<IcpDefect>().orderByDesc(IcpDefect::getId);
        IcpScope.apply(wrapper, info, IcpDefect::getDeptId, IcpDefect::getOwnerUserId);
        if (severity != null) {
            wrapper.eq(IcpDefect::getSeverity, severity);
        }
        if (deptId != null) {
            wrapper.eq(IcpDefect::getDeptId, deptId);
        }
        if (status != null) {
            wrapper.eq(IcpDefect::getStatus, status);
        }
        if (sourceType != null) {
            wrapper.eq(IcpDefect::getSourceType, sourceType);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpDefect::getDefectName, kw)
                    .or().like(IcpDefect::getDefectCode, kw)
                    .or().like(IcpDefect::getDescription, kw));
        }
        Page<IcpDefect> page = defectMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpDefect get(Long id) {
        IcpDefect defect = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), defect.getDeptId(), defect.getOwnerUserId());
        fillNames(List.of(defect));
        return defect;
    }

    @Transactional
    public IcpDefect create(IcpDefect request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        IcpDefect defect = new IcpDefect();
        defect.setDefectName(request.getDefectName() == null ? null : request.getDefectName().trim());
        defect.setSourceType(request.getSourceType() == null ? 1 : request.getSourceType());
        defect.setSourceId(request.getSourceId());
        defect.setSeverity(request.getSeverity() == null ? 2 : request.getSeverity());
        defect.setDefectType(request.getDefectType() == null ? 2 : request.getDefectType());
        defect.setFindDate(request.getFindDate() == null ? LocalDate.now() : request.getFindDate());
        defect.setDiscoverUserId(request.getDiscoverUserId() != null
                ? request.getDiscoverUserId() : SecurityUtils.currentUserIdOrNull());
        defect.setDescription(request.getDescription());
        defect.setRemark(request.getRemark());
        defect.setStatus(REGISTERED);
        if (request.getDeptId() != null) {
            defect.setDeptId(request.getDeptId());
        } else {
            defect.setDeptId(dataScopeService.current().getDeptId());
        }
        defect.setOwnerUserId(request.getOwnerUserId() != null
                ? request.getOwnerUserId() : SecurityUtils.currentUserIdOrNull());
        defect.setDefectCode(StringUtils.hasText(request.getDefectCode())
                ? request.getDefectCode().trim() : CodeSeq.next(defectMapper.selectMaxCode(), "QX", 3));
        validate(defect);
        defectMapper.insert(defect);
        fillNames(List.of(defect));
        return defect;
    }

    @Transactional
    public IcpDefect update(Long id, IcpDefect request) {
        IcpDefect existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getOwnerUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() != null && existing.getStatus() == CLOSED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已关闭的缺陷不可编辑");
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "缺陷状态请使用整改/关闭接口变更");
        }
        if (StringUtils.hasText(request.getDefectName())) {
            existing.setDefectName(request.getDefectName().trim());
        }
        if (request.getSeverity() != null) {
            existing.setSeverity(request.getSeverity());
        }
        if (request.getDefectType() != null) {
            existing.setDefectType(request.getDefectType());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getOwnerUserId() != null) {
            existing.setOwnerUserId(request.getOwnerUserId());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        defectMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    /** 发起整改：缺陷 0 → 1，同时生成一条整改记录（编码 ZG-yyyy-###）。 */
    @Transactional
    public Map<String, Object> rectify(Long id, IcpRequests.DefectRectify request) {
        IcpDefect existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getOwnerUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() == CLOSED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已关闭的缺陷不可发起整改");
        }
        if (rectifyMapper.countOpenByDefect(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该缺陷已存在未完成的整改记录");
        }
        if (!StringUtils.hasText(request.rectifyPlan())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "整改方案不能为空");
        }
        if (request.deadline() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "整改期限不能为空");
        }
        IcpRectify rectify = new IcpRectify();
        rectify.setRectifyCode(CodeSeq.next(rectifyMapper.selectMaxCode(), "ZG", 3));
        rectify.setDefectId(id);
        rectify.setRectifyPlan(request.rectifyPlan());
        rectify.setResponsibleUserId(request.responsibleUserId() != null
                ? request.responsibleUserId() : existing.getOwnerUserId());
        rectify.setDeptId(existing.getDeptId());
        rectify.setDeadline(request.deadline());
        rectify.setStatus(0);
        rectifyMapper.insert(rectify);
        existing.setStatus(RECTIFYING);
        defectMapper.updateById(existing);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defectId", id);
        data.put("defectStatus", existing.getStatus());
        data.put("rectifyId", rectify.getId());
        data.put("rectifyCode", rectify.getRectifyCode());
        return data;
    }

    /** 关闭缺陷：2 → 3，要求存在整改通过记录（status=2）并记录关闭时间。 */
    @Transactional
    public IcpDefect close(Long id) {
        IcpDefect existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != REVIEWING) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待复核状态的缺陷可关闭");
        }
        if (rectifyMapper.countApprovedByDefect(id) == 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "缺陷尚未有整改通过记录，不能关闭");
        }
        existing.setStatus(CLOSED);
        existing.setCloseTime(LocalDateTime.now());
        defectMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    private IcpDefect require(Long id) {
        IcpDefect defect = defectMapper.selectById(id);
        if (defect == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "缺陷不存在");
        }
        return defect;
    }

    private void validate(IcpDefect defect) {
        if (!StringUtils.hasText(defect.getDefectName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺陷名称不能为空");
        }
        if (defect.getDefectName().length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺陷名称不能超过 200 个字符");
        }
        if (defect.getSeverity() == null || defect.getSeverity() < 1 || defect.getSeverity() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "严重程度只能为 1/2/3");
        }
        if (defect.getStatus() != null && (defect.getStatus() < 0 || defect.getStatus() > 3)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺陷状态不合法");
        }
    }

    private void fillNames(List<IcpDefect> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> deptIds = records.stream().map(IcpDefect::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = lookupService.deptNames(deptIds);
        for (IcpDefect record : records) {
            record.setDeptName(depts.get(record.getDeptId()));
            List<IcpRectify> rectifies = rectifyMapper.selectList(new LambdaQueryWrapper<IcpRectify>()
                    .eq(IcpRectify::getDefectId, record.getId())
                    .orderByDesc(IcpRectify::getId));
            record.setRectifyId(rectifies.isEmpty() ? null : rectifies.get(0).getId());
        }
    }
}
