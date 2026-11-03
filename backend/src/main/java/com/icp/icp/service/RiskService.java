package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.entity.IcpRiskCategory;
import com.icp.icp.mapper.IcpRiskCategoryMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.ConfigService;
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
 * 风险库（docs/03 §4 / docs/04 §4）：风险值 = 可能性 × 影响，等级阈值可配置（默认 high≥15 / mid 8–14 / low≤7）。
 */
@Service
public class RiskService {

    private static final int LEVEL_HIGH = 1;
    private static final int LEVEL_MID = 2;
    private static final int LEVEL_LOW = 3;

    private final IcpRiskMapper riskMapper;
    private final IcpRiskCategoryMapper categoryMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;
    private final ConfigService configService;

    public RiskService(IcpRiskMapper riskMapper,
                       IcpRiskCategoryMapper categoryMapper,
                       IcpLookupService lookupService,
                       DataScopeService dataScopeService,
                       ConfigService configService) {
        this.riskMapper = riskMapper;
        this.categoryMapper = categoryMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
    }

    public PageResult<IcpRisk> page(long pageNum, long pageSize, String keyword, Long categoryId,
                                    Integer riskLevel, Integer status) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpRisk> wrapper = new LambdaQueryWrapper<IcpRisk>().orderByDesc(IcpRisk::getId);
        IcpScope.apply(wrapper, info, IcpRisk::getOwnerDeptId, IcpRisk::getOwnerUserId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpRisk::getRiskName, kw)
                    .or().like(IcpRisk::getRiskCode, kw)
                    .or().like(IcpRisk::getDescription, kw));
        }
        if (categoryId != null) {
            wrapper.eq(IcpRisk::getCategoryId, categoryId);
        }
        if (riskLevel != null) {
            wrapper.eq(IcpRisk::getRiskLevel, riskLevel);
        }
        if (status != null) {
            wrapper.eq(IcpRisk::getStatus, status);
        }
        Page<IcpRisk> page = riskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpRisk get(Long id) {
        IcpRisk risk = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), risk.getOwnerDeptId(), risk.getOwnerUserId());
        fillNames(List.of(risk));
        return risk;
    }

    @Transactional
    public IcpRisk create(IcpRisk request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        IcpRisk risk = new IcpRisk();
        risk.setRiskName(request.getRiskName() == null ? null : request.getRiskName().trim());
        risk.setCategoryId(request.getCategoryId());
        risk.setProbability(request.getProbability());
        risk.setImpact(request.getImpact());
        risk.setDescription(request.getDescription());
        risk.setRemark(request.getRemark());
        risk.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (request.getOwnerDeptId() != null) {
            risk.setOwnerDeptId(request.getOwnerDeptId());
        } else {
            risk.setOwnerDeptId(dataScopeService.current().getDeptId());
        }
        risk.setOwnerUserId(request.getOwnerUserId() != null
                ? request.getOwnerUserId() : SecurityUtils.currentUserIdOrNull());
        risk.setRiskCode(StringUtils.hasText(request.getRiskCode())
                ? request.getRiskCode().trim() : CodeSeq.next(riskMapper.selectMaxCode(), "FX", 3));
        applyScore(risk);
        validate(risk);
        riskMapper.insert(risk);
        fillNames(List.of(risk));
        return risk;
    }

    @Transactional
    public IcpRisk update(Long id, IcpRisk request) {
        IcpRisk existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "风险状态请使用 PUT /risks/{id}/status 变更");
        }
        if (StringUtils.hasText(request.getRiskName())) {
            existing.setRiskName(request.getRiskName().trim());
        }
        if (request.getCategoryId() != null) {
            existing.setCategoryId(request.getCategoryId());
        }
        if (request.getProbability() != null) {
            existing.setProbability(request.getProbability());
        }
        if (request.getImpact() != null) {
            existing.setImpact(request.getImpact());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        applyScore(existing);
        validate(existing);
        riskMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        IcpRisk existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (riskMapper.countMatrixRefs(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风险已被风险矩阵引用，不能删除");
        }
        riskMapper.deleteById(id);
    }

    @Transactional
    public IcpRisk changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 0（停用）或 1（启用）");
        }
        IcpRisk existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        existing.setStatus(status);
        riskMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    private void applyScore(IcpRisk risk) {
        if (risk.getProbability() == null || risk.getImpact() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "可能性与影响程度不能为空");
        }
        if (risk.getProbability() < 1 || risk.getProbability() > 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "可能性取值范围为 1-5");
        }
        if (risk.getImpact() < 1 || risk.getImpact() > 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "影响程度取值范围为 1-5");
        }
        int score = risk.getProbability() * risk.getImpact();
        int high = configService.getInt("icp.risk.highScore", 15);
        int mid = configService.getInt("icp.risk.midScore", 8);
        risk.setRiskScore(score);
        if (score >= high) {
            risk.setRiskLevel(LEVEL_HIGH);
        } else if (score >= mid) {
            risk.setRiskLevel(LEVEL_MID);
        } else {
            risk.setRiskLevel(LEVEL_LOW);
        }
    }

    private IcpRisk require(Long id) {
        IcpRisk risk = riskMapper.selectById(id);
        if (risk == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风险不存在");
        }
        return risk;
    }

    private void validate(IcpRisk risk) {
        if (!StringUtils.hasText(risk.getRiskName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风险名称不能为空");
        }
        if (risk.getRiskName().length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风险名称不能超过 200 个字符");
        }
        if (risk.getCategoryId() != null) {
            IcpRiskCategory category = categoryMapper.selectById(risk.getCategoryId());
            if (category == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "风险分类不存在");
            }
        }
        if (risk.getStatus() != null && risk.getStatus() != 0 && risk.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
    }

    private void fillNames(List<IcpRisk> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = records.stream().map(IcpRisk::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> deptIds = records.stream().map(IcpRisk::getOwnerDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categories = lookupService.riskCategoryNames(categoryIds);
        Map<Long, String> depts = lookupService.deptNames(deptIds);
        for (IcpRisk record : records) {
            record.setCategoryName(categories.get(record.getCategoryId()));
            record.setOwnerDeptName(depts.get(record.getOwnerDeptId()));
        }
    }
}
