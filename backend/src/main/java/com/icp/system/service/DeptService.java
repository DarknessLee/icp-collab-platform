package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门管理：树/列表/增改删；维护 ancestors 并在移动部门时级联刷新下级。
 */
@Service
public class DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final ChangeLogService changeLogService;

    public DeptService(SysDeptMapper deptMapper, SysUserMapper userMapper, ChangeLogService changeLogService) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.changeLogService = changeLogService;
    }

    public List<SysDept> tree() {
        List<SysDept> all = rawList();
        fillLeaderNames(all);
        Map<Long, SysDept> nodes = new LinkedHashMap<>();
        for (SysDept dept : all) {
            dept.setChildren(new ArrayList<>());
            nodes.put(dept.getId(), dept);
        }
        List<SysDept> roots = new ArrayList<>();
        for (SysDept dept : nodes.values()) {
            SysDept parent = dept.getParentId() == null ? null : nodes.get(dept.getParentId());
            if (parent == null || dept.getParentId() == null || dept.getParentId() == 0L) {
                roots.add(dept);
            } else {
                parent.getChildren().add(dept);
            }
        }
        return roots;
    }

    public List<SysDept> list() {
        List<SysDept> all = rawList();
        fillLeaderNames(all);
        return all;
    }

    public SysDept get(Long id) {
        SysDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        fillLeaderNames(List.of(dept));
        return dept;
    }

    @Transactional
    public SysDept create(SysDept request) {
        validate(request, null);
        SysDept dept = new SysDept();
        dept.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        dept.setDeptName(request.getDeptName().trim());
        dept.setDeptCode(resolveCreateCode(request.getDeptCode()));
        dept.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        dept.setLeaderUserId(request.getLeaderUserId());
        dept.setPhone(trimToNull(request.getPhone()));
        dept.setEmail(trimToNull(request.getEmail()));
        dept.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        dept.setRemark(request.getRemark());
        dept.setAncestors(resolveAncestors(dept.getParentId()));
        checkCodeUnique(dept.getDeptCode(), null);
        deptMapper.insert(dept);
        changeLogService.record("sys_dept", dept.getId(), null, dept);
        return dept;
    }

    @Transactional
    public SysDept update(Long id, SysDept request) {
        SysDept existing = get(id);
        SysDept before = copyOf(existing);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        Long newParentId = request.getParentId() == null ? existing.getParentId() : request.getParentId();
        newParentId = newParentId == null ? 0L : newParentId;
        boolean moved = !newParentId.equals(existing.getParentId() == null ? 0L : existing.getParentId());
        String newAncestors = existing.getAncestors();
        if (moved) {
            if (newParentId != 0L && (newParentId.equals(id) || isDescendant(id, newParentId))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能把部门移动到自身或下级部门");
            }
            newAncestors = resolveAncestors(newParentId);
        }
        existing.setParentId(newParentId);
        existing.setAncestors(newAncestors);
        existing.setDeptName(StringUtils.hasText(request.getDeptName())
                ? request.getDeptName().trim() : existing.getDeptName());
        String code = trimToNull(request.getDeptCode());
        existing.setDeptCode(code == null ? existing.getDeptCode() : code);
        existing.setOrderNum(request.getOrderNum() == null ? existing.getOrderNum() : request.getOrderNum());
        existing.setLeaderUserId(request.getLeaderUserId() == null
                ? existing.getLeaderUserId() : request.getLeaderUserId());
        existing.setPhone(request.getPhone() == null ? existing.getPhone() : trimToNull(request.getPhone()));
        existing.setEmail(request.getEmail() == null ? existing.getEmail() : trimToNull(request.getEmail()));
        existing.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        existing.setRemark(request.getRemark() == null ? existing.getRemark() : request.getRemark());
        validate(existing, id);
        checkCodeUnique(existing.getDeptCode(), id);
        deptMapper.updateById(existing);
        if (moved) {
            cascadeAncestors(id, newAncestors);
        }
        changeLogService.record("sys_dept", id, before, existing);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysDept dept = get(id);
        long children = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "存在下级部门，不能删除");
        }
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (users > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "部门下存在用户，不能删除");
        }
        deptMapper.deleteById(id);
        changeLogService.record("sys_dept", id, dept, null);
    }

    private List<SysDept> rawList() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getOrderNum)
                .orderByAsc(SysDept::getId));
    }

    private void fillLeaderNames(List<SysDept> depts) {
        List<Long> leaderIds = depts.stream().map(SysDept::getLeaderUserId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (leaderIds.isEmpty()) {
            return;
        }
        List<SysUser> users = userMapper.selectBatchIds(leaderIds);
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : users) {
            names.put(user.getId(), user.getRealName());
        }
        for (SysDept dept : depts) {
            dept.setLeaderName(names.get(dept.getLeaderUserId()));
        }
    }

    private void validate(SysDept request, Long id) {
        if (request == null || !StringUtils.hasText(request.getDeptName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门名称不能为空");
        }
        if (request.getDeptName().trim().length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门名称不能超过 50 个字符");
        }
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        if (parentId != 0L) {
            SysDept parent = deptMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级部门不存在");
            }
            if (id == null && parent.getStatus() != null && parent.getStatus() == 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "上级部门已停用");
            }
        } else if (id == null) {
            long roots = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getParentId, 0));
            if (roots > 0) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "已存在根部门，不能重复创建");
            }
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门状态不合法");
        }
        if (request.getLeaderUserId() != null) {
            SysUser leader = userMapper.selectById(request.getLeaderUserId());
            if (leader == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "负责人不存在");
            }
        }
    }

    private String resolveAncestors(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return "0";
        }
        SysDept parent = deptMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上级部门不存在");
        }
        return (parent.getAncestors() == null ? "0" : parent.getAncestors()) + "," + parent.getId();
    }

    /**
     * 部门编码：请求未带时自动生成 D + 4 位序号（dept_code 为 NOT NULL 且唯一，缺失不能落库）。
     */
    private String resolveCreateCode(String requested) {
        String code = trimToNull(requested);
        if (code != null) {
            return code;
        }
        long next = deptMapper.selectMaxAutoCode() + 1;
        while (next < 1000000L) {
            String candidate = String.format("D%04d", next);
            SysDept existing = deptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                    .eq(SysDept::getDeptCode, candidate).last("LIMIT 1"));
            if (existing == null) {
                return candidate;
            }
            next++;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法生成部门编码");
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        SysDept existing = deptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeptCode, code).last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门编码已存在");
        }
    }

    private boolean isDescendant(Long ancestorId, Long candidateId) {
        List<SysDept> all = deptMapper.selectList(null);
        Map<Long, Long> parentIndex = new HashMap<>();
        for (SysDept dept : all) {
            parentIndex.put(dept.getId(), dept.getParentId() == null ? 0L : dept.getParentId());
        }
        Long current = candidateId;
        int guard = 0;
        while (current != null && current != 0L && guard++ < 64) {
            if (current.equals(ancestorId)) {
                return true;
            }
            current = parentIndex.get(current);
        }
        return false;
    }

    private void cascadeAncestors(Long deptId, String newAncestors) {
        List<SysDept> all = deptMapper.selectList(null);
        Map<Long, SysDept> byId = new HashMap<>();
        for (SysDept dept : all) {
            byId.put(dept.getId(), dept);
        }
        SysDept current = byId.get(deptId);
        if (current == null) {
            return;
        }
        current.setAncestors(newAncestors);
        List<SysDept> descendants = all.stream()
                .filter(dept -> !dept.getId().equals(deptId) && isDescendant(deptId, dept.getId()))
                .sorted(Comparator.comparing(dept -> depth(dept.getAncestors())))
                .collect(Collectors.toList());
        for (SysDept child : descendants) {
            SysDept parent = byId.get(child.getParentId());
            if (parent == null) {
                continue;
            }
            String ancestors = (parent.getAncestors() == null ? "0" : parent.getAncestors())
                    + "," + parent.getId();
            child.setAncestors(ancestors);
            deptMapper.updateById(child);
        }
    }

    private int depth(String ancestors) {
        if (ancestors == null) {
            return 0;
        }
        return ancestors.split(",").length;
    }

    private SysDept copyOf(SysDept source) {
        SysDept copy = new SysDept();
        copy.setId(source.getId());
        copy.setParentId(source.getParentId());
        copy.setAncestors(source.getAncestors());
        copy.setDeptName(source.getDeptName());
        copy.setDeptCode(source.getDeptCode());
        copy.setOrderNum(source.getOrderNum());
        copy.setLeaderUserId(source.getLeaderUserId());
        copy.setPhone(source.getPhone());
        copy.setEmail(source.getEmail());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
