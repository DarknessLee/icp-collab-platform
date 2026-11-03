package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.entity.IcpRiskCategory;
import com.icp.icp.mapper.IcpRiskCategoryMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险分类：树查询与增删改（docs/03 §4 风险分类）。
 */
@Service
public class RiskCategoryService {

    private final IcpRiskCategoryMapper categoryMapper;
    private final IcpRiskMapper riskMapper;

    public RiskCategoryService(IcpRiskCategoryMapper categoryMapper, IcpRiskMapper riskMapper) {
        this.categoryMapper = categoryMapper;
        this.riskMapper = riskMapper;
    }

    public List<IcpRiskCategory> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<IcpRiskCategory>()
                .orderByAsc(IcpRiskCategory::getOrderNum)
                .orderByAsc(IcpRiskCategory::getId));
    }

    public List<IcpRiskCategory> tree() {
        List<IcpRiskCategory> all = listAll();
        Map<Long, IcpRiskCategory> index = new LinkedHashMap<>();
        for (IcpRiskCategory node : all) {
            node.setChildren(new ArrayList<>());
            index.put(node.getId(), node);
        }
        List<IcpRiskCategory> roots = new ArrayList<>();
        for (IcpRiskCategory node : all) {
            IcpRiskCategory parent = node.getParentId() == null ? null : index.get(node.getParentId());
            if (parent == null || parent.getId().equals(node.getId())) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    public IcpRiskCategory get(Long id) {
        IcpRiskCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "风险分类不存在");
        }
        return category;
    }

    @Transactional
    public IcpRiskCategory create(IcpRiskCategory request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        validate(request, null);
        IcpRiskCategory category = new IcpRiskCategory();
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setCategoryName(request.getCategoryName().trim());
        category.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        category.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public IcpRiskCategory update(Long id, IcpRiskCategory request) {
        IcpRiskCategory existing = get(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (StringUtils.hasText(request.getCategoryName())) {
            existing.setCategoryName(request.getCategoryName().trim());
        }
        if (request.getParentId() != null) {
            existing.setParentId(request.getParentId());
        }
        if (request.getOrderNum() != null) {
            existing.setOrderNum(request.getOrderNum());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        validate(existing, id);
        categoryMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        IcpRiskCategory existing = get(id);
        long children = categoryMapper.selectCount(new LambdaQueryWrapper<IcpRiskCategory>()
                .eq(IcpRiskCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在子分类，不能删除");
        }
        long refs = riskMapper.selectCount(new LambdaQueryWrapper<IcpRisk>()
                .eq(IcpRisk::getCategoryId, id));
        if (refs > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类下存在风险，不能删除");
        }
        categoryMapper.deleteById(existing.getId());
    }

    private void validate(IcpRiskCategory request, Long selfId) {
        if (!StringUtils.hasText(request.getCategoryName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类名称不能为空");
        }
        if (request.getCategoryName().trim().length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类名称不能超过 64 个字符");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 0 或 1");
        }
        Long parentId = request.getParentId();
        if (parentId != null && parentId > 0) {
            if (selfId != null && selfId.equals(parentId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级分类不能是自己");
            }
            IcpRiskCategory parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级分类不存在");
            }
            if (selfId != null && isDescendant(selfId, parentId)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级分类不能是自己的下级");
            }
        }
    }

    private boolean isDescendant(Long rootId, Long candidateId) {
        Long cursor = candidateId;
        int guard = 0;
        while (cursor != null && cursor > 0 && guard++ < 32) {
            if (cursor.equals(rootId)) {
                return true;
            }
            IcpRiskCategory parent = categoryMapper.selectById(cursor);
            if (parent == null) {
                return false;
            }
            cursor = parent.getParentId();
        }
        return false;
    }
}
