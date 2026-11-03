package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpPolicyCategory;
import com.icp.icp.mapper.IcpPolicyCategoryMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制度分类：树查询与增删改（docs/03 §4 政策分类）。
 */
@Service
public class PolicyCategoryService {

    private final IcpPolicyCategoryMapper categoryMapper;
    private final IcpPolicyMapper policyMapper;

    public PolicyCategoryService(IcpPolicyCategoryMapper categoryMapper, IcpPolicyMapper policyMapper) {
        this.categoryMapper = categoryMapper;
        this.policyMapper = policyMapper;
    }

    public List<IcpPolicyCategory> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<IcpPolicyCategory>()
                .orderByAsc(IcpPolicyCategory::getOrderNum)
                .orderByAsc(IcpPolicyCategory::getId));
    }

    public List<IcpPolicyCategory> tree() {
        List<IcpPolicyCategory> all = listAll();
        Map<Long, IcpPolicyCategory> index = new LinkedHashMap<>();
        for (IcpPolicyCategory node : all) {
            node.setChildren(new ArrayList<>());
            index.put(node.getId(), node);
        }
        List<IcpPolicyCategory> roots = new ArrayList<>();
        for (IcpPolicyCategory node : all) {
            IcpPolicyCategory parent = node.getParentId() == null ? null : index.get(node.getParentId());
            if (parent == null || parent.getId().equals(node.getId())) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    public IcpPolicyCategory get(Long id) {
        IcpPolicyCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "制度分类不存在");
        }
        return category;
    }

    @Transactional
    public IcpPolicyCategory create(IcpPolicyCategory request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        validate(request, null);
        IcpPolicyCategory category = new IcpPolicyCategory();
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setCategoryName(request.getCategoryName().trim());
        category.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        category.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public IcpPolicyCategory update(Long id, IcpPolicyCategory request) {
        IcpPolicyCategory existing = get(id);
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
        IcpPolicyCategory existing = get(id);
        long children = categoryMapper.selectCount(new LambdaQueryWrapper<IcpPolicyCategory>()
                .eq(IcpPolicyCategory::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在子分类，不能删除");
        }
        long refs = policyMapper.selectCount(new LambdaQueryWrapper<IcpPolicy>()
                .eq(IcpPolicy::getCategoryId, id));
        if (refs > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类下存在制度，不能删除");
        }
        categoryMapper.deleteById(existing.getId());
    }

    private void validate(IcpPolicyCategory request, Long selfId) {
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
            IcpPolicyCategory parent = categoryMapper.selectById(parentId);
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
            IcpPolicyCategory parent = categoryMapper.selectById(cursor);
            if (parent == null) {
                return false;
            }
            cursor = parent.getParentId();
        }
        return false;
    }
}
