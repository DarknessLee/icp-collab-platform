package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpControl;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpControlMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 风险控制矩阵（docs/03 §4 / docs/04 §7 V7）：同一 (risk_id, control_id) 唯一，覆盖率按风险维度统计。
 */
@Service
public class MatrixService {

    private final IcpMatrixMapper matrixMapper;
    private final IcpRiskMapper riskMapper;
    private final IcpControlMapper controlMapper;
    private final IcpPolicyMapper policyMapper;
    private final IcpLookupService lookupService;

    public MatrixService(IcpMatrixMapper matrixMapper,
                         IcpRiskMapper riskMapper,
                         IcpControlMapper controlMapper,
                         IcpPolicyMapper policyMapper,
                         IcpLookupService lookupService) {
        this.matrixMapper = matrixMapper;
        this.riskMapper = riskMapper;
        this.controlMapper = controlMapper;
        this.policyMapper = policyMapper;
        this.lookupService = lookupService;
    }

    public PageResult<IcpMatrix> page(long pageNum, long pageSize, Long riskId, Long controlId,
                                      Integer coverage, String processKey) {
        LambdaQueryWrapper<IcpMatrix> wrapper = new LambdaQueryWrapper<IcpMatrix>().orderByDesc(IcpMatrix::getId);
        if (riskId != null) {
            wrapper.eq(IcpMatrix::getRiskId, riskId);
        }
        if (controlId != null) {
            wrapper.eq(IcpMatrix::getControlId, controlId);
        }
        if (coverage != null) {
            wrapper.eq(IcpMatrix::getCoverage, coverage);
        }
        if (StringUtils.hasText(processKey)) {
            wrapper.eq(IcpMatrix::getProcessKey, processKey.trim());
        }
        Page<IcpMatrix> page = matrixMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillLabels(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpMatrix get(Long id) {
        IcpMatrix matrix = matrixMapper.selectById(id);
        if (matrix == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "矩阵记录不存在");
        }
        fillLabels(List.of(matrix));
        return matrix;
    }

    @Transactional
    public IcpMatrix create(IcpMatrix request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        IcpMatrix matrix = new IcpMatrix();
        matrix.setRiskId(request.getRiskId());
        matrix.setControlId(request.getControlId());
        matrix.setPolicyId(request.getPolicyId());
        matrix.setProcessKey(request.getProcessKey());
        matrix.setPostId(request.getPostId());
        matrix.setCoverage(request.getCoverage());
        matrix.setEvidenceDesc(request.getEvidenceDesc());
        matrix.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        matrix.setRemark(request.getRemark());
        matrix.setMatrixCode(StringUtils.hasText(request.getMatrixCode())
                ? request.getMatrixCode().trim() : CodeSeq.next(matrixMapper.selectMaxCode(), "JC", 3));
        validate(matrix, null);
        matrixMapper.insert(matrix);
        fillLabels(List.of(matrix));
        return matrix;
    }

    @Transactional
    public IcpMatrix update(Long id, IcpMatrix request) {
        IcpMatrix existing = getEntity(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (request.getRiskId() != null) {
            existing.setRiskId(request.getRiskId());
        }
        if (request.getControlId() != null) {
            existing.setControlId(request.getControlId());
        }
        if (request.getPolicyId() != null) {
            existing.setPolicyId(request.getPolicyId());
        }
        if (request.getProcessKey() != null) {
            existing.setProcessKey(request.getProcessKey());
        }
        if (request.getPostId() != null) {
            existing.setPostId(request.getPostId());
        }
        if (request.getCoverage() != null) {
            existing.setCoverage(request.getCoverage());
        }
        if (request.getEvidenceDesc() != null) {
            existing.setEvidenceDesc(request.getEvidenceDesc());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing, id);
        matrixMapper.updateById(existing);
        fillLabels(List.of(existing));
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        getEntity(id);
        matrixMapper.deleteById(id);
    }

    /**
     * 覆盖率口径（docs/03 §4 GET /matrices/coverage）：按风险统计已配置控制点数量，
     * 覆盖风险 = 该风险在 icp_matrix 中至少有一条未删除记录；覆盖率 = 覆盖风险数 / 风险总数。
     */
    public Map<String, Object> coverage() {
        List<IcpRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<IcpRisk>()
                .eq(IcpRisk::getStatus, 1)
                .orderByAsc(IcpRisk::getRiskCode));
        List<IcpMatrix> matrices = matrixMapper.selectList(new LambdaQueryWrapper<IcpMatrix>()
                .eq(IcpMatrix::getStatus, 1));
        Map<Long, List<IcpMatrix>> byRisk = new HashMap<>();
        for (IcpMatrix matrix : matrices) {
            byRisk.computeIfAbsent(matrix.getRiskId(), k -> new ArrayList<>()).add(matrix);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int covered = 0;
        for (IcpRisk risk : risks) {
            List<IcpMatrix> rows = byRisk.getOrDefault(risk.getId(), List.of());
            if (!rows.isEmpty()) {
                covered++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("riskId", risk.getId());
            item.put("riskCode", risk.getRiskCode());
            item.put("riskName", risk.getRiskName());
            item.put("riskLevel", risk.getRiskLevel());
            item.put("controlCount", rows.size());
            item.put("covered", !rows.isEmpty());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRisks", risks.size());
        result.put("coveredRisks", covered);
        result.put("uncoveredRisks", risks.size() - covered);
        result.put("coverageRate", risks.isEmpty() ? 0D : Math.round(covered * 10000.0 / risks.size()) / 100.0);
        result.put("items", items);
        return result;
    }

    private IcpMatrix getEntity(Long id) {
        IcpMatrix matrix = matrixMapper.selectById(id);
        if (matrix == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "矩阵记录不存在");
        }
        return matrix;
    }

    private void validate(IcpMatrix matrix, Long selfId) {
        if (matrix.getRiskId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风险不能为空");
        }
        if (matrix.getControlId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "控制点不能为空");
        }
        IcpRisk risk = riskMapper.selectById(matrix.getRiskId());
        if (risk == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风险不存在");
        }
        IcpControl control = controlMapper.selectById(matrix.getControlId());
        if (control == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "控制点不存在");
        }
        if (matrix.getPolicyId() != null) {
            IcpPolicy policy = policyMapper.selectById(matrix.getPolicyId());
            if (policy == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "制度不存在");
            }
        }
        if (matrix.getCoverage() != null && (matrix.getCoverage() < 1 || matrix.getCoverage() > 3)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "覆盖程度只能为 1/2/3");
        }
        if (matrix.getStatus() != null && matrix.getStatus() != 0 && matrix.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
        LambdaQueryWrapper<IcpMatrix> duplicate = new LambdaQueryWrapper<IcpMatrix>()
                .eq(IcpMatrix::getRiskId, matrix.getRiskId())
                .eq(IcpMatrix::getControlId, matrix.getControlId());
        if (selfId != null) {
            duplicate.ne(IcpMatrix::getId, selfId);
        }
        if (matrixMapper.selectCount(duplicate) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "同一风险与控制点的矩阵关系已存在");
        }
    }

    private void fillLabels(List<IcpMatrix> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> riskIds = records.stream().map(IcpMatrix::getRiskId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> controlIds = records.stream().map(IcpMatrix::getControlId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> risks = lookupService.riskLabels(riskIds);
        Map<Long, String> controls = lookupService.controlLabels(controlIds);
        for (IcpMatrix record : records) {
            record.setRiskName(risks.get(record.getRiskId()));
            record.setControlName(controls.get(record.getControlId()));
        }
    }
}
