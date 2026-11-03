package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysDictData;
import com.icp.system.entity.SysDictType;
import com.icp.system.mapper.SysDictDataMapper;
import com.icp.system.mapper.SysDictTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 字典管理：类型分页、按类型取数据项、类型/数据项增改删。
 *
 * <p>内置字典类型判定：sys_dict_type 无内置标记列（与 sys_config.config_type='Y' 不同），
 * 因此约定 V2 种子提供的 20 个字典类型为内置、只读删除；业务新增的类型允许删除。</p>
 */
@Service
public class DictService {

    /** V2__seed_base.sql 中的 20 个内置字典类型编码。 */
    private static final Set<String> BUILTIN_DICT_TYPES = Set.of(
            "icp_policy_status", "icp_risk_level", "icp_risk_category", "icp_control_type",
            "icp_eval_status", "icp_eval_conclusion", "icp_defect_severity", "icp_defect_source",
            "icp_defect_type", "icp_rectify_status", "coll_task_priority", "coll_task_status",
            "coll_doc_type", "coll_notice_type", "coll_message_type", "wf_business_status",
            "sys_data_scope", "sys_user_gender", "wf_seal_type", "wf_purchase_type");

    private final SysDictTypeMapper typeMapper;
    private final SysDictDataMapper dataMapper;

    public DictService(SysDictTypeMapper typeMapper, SysDictDataMapper dataMapper) {
        this.typeMapper = typeMapper;
        this.dataMapper = dataMapper;
    }

    public PageResult<SysDictType> pageTypes(long pageNum, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .orderByAsc(SysDictType::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysDictType::getDictName, keyword.trim())
                    .or().like(SysDictType::getDictType, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(SysDictType::getStatus, status);
        }
        Page<SysDictType> page = typeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public List<SysDictData> items(String dictType, Integer status) {
        if (!StringUtils.hasText(dictType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典类型不能为空");
        }
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType.trim())
                .orderByAsc(SysDictData::getDictSort)
                .orderByAsc(SysDictData::getId);
        if (status != null) {
            wrapper.eq(SysDictData::getStatus, status);
        }
        return dataMapper.selectList(wrapper);
    }

    @Transactional
    public SysDictType createType(SysDictType request) {
        validateType(request, null);
        SysDictType type = new SysDictType();
        type.setDictName(request.getDictName().trim());
        type.setDictType(request.getDictType().trim());
        type.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        type.setRemark(request.getRemark());
        typeMapper.insert(type);
        return type;
    }

    @Transactional
    public SysDictType updateType(Long id, SysDictType request) {
        SysDictType existing = requireType(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (request.getDictType() != null && !request.getDictType().trim().equals(existing.getDictType())) {
            long used = dataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, existing.getDictType()));
            if (used > 0) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "字典类型已有数据项，不能修改类型编码");
            }
            existing.setDictType(request.getDictType().trim());
        }
        if (StringUtils.hasText(request.getDictName())) {
            existing.setDictName(request.getDictName().trim());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validateType(existing, id);
        typeMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public SysDictData createItem(SysDictData request) {
        validateItem(request, null);
        SysDictData item = new SysDictData();
        copyItem(request, item);
        if (item.getDictSort() == null) {
            item.setDictSort(0);
        }
        if (item.getStatus() == null) {
            item.setStatus(1);
        }
        dataMapper.insert(item);
        return item;
    }

    @Transactional
    public SysDictData updateItem(Long id, SysDictData request) {
        SysDictData existing = requireItem(id);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (StringUtils.hasText(request.getDictType())) {
            existing.setDictType(request.getDictType().trim());
        }
        if (StringUtils.hasText(request.getDictLabel())) {
            existing.setDictLabel(request.getDictLabel().trim());
        }
        if (StringUtils.hasText(request.getDictValue())) {
            existing.setDictValue(request.getDictValue().trim());
        }
        if (request.getDictSort() != null) {
            existing.setDictSort(request.getDictSort());
        }
        if (request.getTagType() != null) {
            existing.setTagType(request.getTagType());
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validateItem(existing, id);
        dataMapper.updateById(existing);
        return existing;
    }

    @Transactional
    public void deleteItem(Long id) {
        requireItem(id);
        dataMapper.deleteById(id);
    }

    /**
     * 删除字典类型：内置类型禁删（40001），仍有字典项时禁删（40000），重复删除 40400。
     */
    @Transactional
    public void deleteType(Long id) {
        SysDictType existing = requireType(id);
        if (BUILTIN_DICT_TYPES.contains(existing.getDictType())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "内置字典类型不允许删除");
        }
        long items = dataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, existing.getDictType()));
        if (items > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先删除字典项");
        }
        typeMapper.deleteById(id);
    }

    private SysDictType requireType(Long id) {
        SysDictType type = typeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return type;
    }

    private SysDictData requireItem(Long id) {
        SysDictData item = dataMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典数据不存在");
        }
        return item;
    }

    private void validateType(SysDictType request, Long selfId) {
        if (request == null || !StringUtils.hasText(request.getDictName())
                || !StringUtils.hasText(request.getDictType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典名称与字典类型不能为空");
        }
        LambdaQueryWrapper<SysDictType> duplicate = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, request.getDictType().trim()).last("LIMIT 1");
        if (selfId != null) {
            duplicate.ne(SysDictType::getId, selfId);
        }
        if (typeMapper.selectOne(duplicate) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典类型已存在");
        }
    }

    private void validateItem(SysDictData request, Long selfId) {
        if (request == null || !StringUtils.hasText(request.getDictType())
                || !StringUtils.hasText(request.getDictLabel())
                || !StringUtils.hasText(request.getDictValue())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "字典类型/标签/键值不能为空");
        }
        LambdaQueryWrapper<SysDictData> duplicate = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, request.getDictType().trim())
                .eq(SysDictData::getDictValue, request.getDictValue().trim())
                .last("LIMIT 1");
        if (selfId != null) {
            duplicate.ne(SysDictData::getId, selfId);
        }
        if (dataMapper.selectOne(duplicate) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "同一字典下键值不能重复");
        }
    }

    private void copyItem(SysDictData source, SysDictData target) {
        target.setDictType(source.getDictType().trim());
        target.setDictLabel(source.getDictLabel().trim());
        target.setDictValue(source.getDictValue().trim());
        target.setDictSort(source.getDictSort());
        target.setTagType(source.getTagType());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
    }
}
