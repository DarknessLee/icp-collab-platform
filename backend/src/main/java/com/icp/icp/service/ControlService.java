package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.entity.IcpControl;
import com.icp.icp.mapper.IcpControlMapper;
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 控制点库（docs/03 §4 控制点）：控制类型 1 预防 / 2 检测 / 3 纠正。
 */
@Service
public class ControlService {

    private final IcpControlMapper controlMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;

    public ControlService(IcpControlMapper controlMapper,
                          IcpLookupService lookupService,
                          DataScopeService dataScopeService) {
        this.controlMapper = controlMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<IcpControl> page(long pageNum, long pageSize, String keyword,
                                       Integer controlType, Integer status) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpControl> wrapper = new LambdaQueryWrapper<IcpControl>().orderByDesc(IcpControl::getId);
        IcpScope.apply(wrapper, info, IcpControl::getOwnerDeptId, IcpControl::getOwnerUserId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpControl::getControlName, kw)
                    .or().like(IcpControl::getControlCode, kw)
                    .or().like(IcpControl::getControlDesc, kw));
        }
        if (controlType != null) {
            wrapper.eq(IcpControl::getControlType, controlType);
        }
        if (status != null) {
            wrapper.eq(IcpControl::getStatus, status);
        }
        Page<IcpControl> page = controlMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpControl get(Long id) {
        IcpControl control = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), control.getOwnerDeptId(), control.getOwnerUserId());
        fillNames(List.of(control));
        return control;
    }

    @Transactional
    public IcpControl create(IcpControl request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        IcpControl control = new IcpControl();
        control.setControlName(request.getControlName() == null ? null : request.getControlName().trim());
        control.setControlType(request.getControlType());
        control.setControlDesc(request.getControlDesc());
        control.setFreq(request.getFreq());
        control.setRemark(request.getRemark());
        control.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (request.getOwnerDeptId() != null) {
            control.setOwnerDeptId(request.getOwnerDeptId());
        } else {
            control.setOwnerDeptId(dataScopeService.current().getDeptId());
        }
        control.setOwnerUserId(request.getOwnerUserId() != null
                ? request.getOwnerUserId() : SecurityUtils.currentUserIdOrNull());
        control.setControlCode(StringUtils.hasText(request.getControlCode())
                ? request.getControlCode().trim() : CodeSeq.next(controlMapper.selectMaxCode(), "KZ", 3));
        validate(control);
        controlMapper.insert(control);
        fillNames(List.of(control));
        return control;
    }

    @Transactional
    public IcpControl update(Long id, IcpControl request) {
        IcpControl existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (StringUtils.hasText(request.getControlName())) {
            existing.setControlName(request.getControlName().trim());
        }
        if (request.getControlType() != null) {
            existing.setControlType(request.getControlType());
        }
        if (request.getControlDesc() != null) {
            existing.setControlDesc(request.getControlDesc());
        }
        if (request.getFreq() != null) {
            existing.setFreq(request.getFreq());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        controlMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        IcpControl existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (controlMapper.countMatrixRefs(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "控制点已被风险矩阵引用，不能删除");
        }
        controlMapper.deleteById(id);
    }

    private IcpControl require(Long id) {
        IcpControl control = controlMapper.selectById(id);
        if (control == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "控制点不存在");
        }
        return control;
    }

    private void validate(IcpControl control) {
        if (!StringUtils.hasText(control.getControlName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "控制点名称不能为空");
        }
        if (control.getControlName().length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "控制点名称不能超过 200 个字符");
        }
        if (control.getControlType() != null
                && (control.getControlType() < 1 || control.getControlType() > 3)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "控制类型只能为 1（预防）/2（检测）/3（纠正）");
        }
        if (control.getStatus() != null && control.getStatus() != 0 && control.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
    }

    private void fillNames(List<IcpControl> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> deptIds = records.stream().map(IcpControl::getOwnerDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = lookupService.deptNames(deptIds);
        for (IcpControl record : records) {
            record.setOwnerDeptName(depts.get(record.getOwnerDeptId()));
        }
    }
}
