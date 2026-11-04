package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.collab.entity.CollDocCategory;
import com.icp.collab.mapper.CollDocCategoryMapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档分类（树形，全局共享，无数据权限）。
 */
@Service
public class DocCategoryService {

    private final CollDocCategoryMapper categoryMapper;

    public DocCategoryService(CollDocCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<CollDocCategory> tree() {
        List<CollDocCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<CollDocCategory>()
                .orderByAsc(CollDocCategory::getOrderNum).orderByAsc(CollDocCategory::getId));
        Map<Long, CollDocCategory> index = new LinkedHashMap<>();
        for (CollDocCategory category : all) {
            category.setChildren(new ArrayList<>());
            index.put(category.getId(), category);
        }
        List<CollDocCategory> roots = new ArrayList<>();
        for (CollDocCategory category : all) {
            Long parentId = category.getParentId();
            if (parentId == null || parentId == 0L || !index.containsKey(parentId)) {
                roots.add(category);
            } else {
                index.get(parentId).getChildren().add(category);
            }
        }
        return roots;
    }

    public List<CollDocCategory> list() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CollDocCategory>()
                .orderByAsc(CollDocCategory::getOrderNum).orderByAsc(CollDocCategory::getId));
    }

    public CollDocCategory get(Long id) {
        CollDocCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档分类不存在");
        }
        return category;
    }

    @Transactional
    public CollDocCategory create(CollDocCategory request) {
        CollDocCategory category = new CollDocCategory();
        category.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        category.setCategoryName(requireName(request.getCategoryName()));
        category.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        category.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        if (category.getParentId() > 0) {
            get(category.getParentId());
        }
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public CollDocCategory update(Long id, CollDocCategory request) {
        CollDocCategory category = get(id);
        if (request.getParentId() != null && !Objects.equals(request.getParentId(), category.getParentId())) {
            validateParent(id, request.getParentId());
            category.setParentId(request.getParentId());
        }
        if (request.getCategoryName() != null) {
            category.setCategoryName(requireName(request.getCategoryName()));
        }
        if (request.getOrderNum() != null) {
            category.setOrderNum(request.getOrderNum());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        if (categoryMapper.countChildren(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先删除子分类");
        }
        if (categoryMapper.countDocs(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该分类下存在文档，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    private void validateParent(Long id, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (Objects.equals(id, parentId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上级分类不能是自身");
        }
        CollDocCategory cursor = get(parentId);
        int guard = 0;
        while (cursor != null && cursor.getParentId() != null && cursor.getParentId() > 0) {
            if (Objects.equals(cursor.getParentId(), id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能将分类移动到其子分类下");
            }
            cursor = categoryMapper.selectById(cursor.getParentId());
            if (++guard > 32) {
                break;
            }
        }
    }

    private String requireName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类名称不能为空");
        }
        String name = value.trim();
        if (name.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类名称长度不能超过 64");
        }
        return name;
    }
}
