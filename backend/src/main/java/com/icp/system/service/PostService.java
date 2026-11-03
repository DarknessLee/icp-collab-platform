package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.system.entity.SysPost;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysPostMapper;
import com.icp.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 岗位管理。 */
@Service
public class PostService {

    private final SysPostMapper postMapper;
    private final SysUserMapper userMapper;
    private final ChangeLogService changeLogService;

    public PostService(SysPostMapper postMapper, SysUserMapper userMapper, ChangeLogService changeLogService) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.changeLogService = changeLogService;
    }

    public PageResult<SysPost> page(long pageNum, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<SysPost>()
                .orderByAsc(SysPost::getOrderNum)
                .orderByAsc(SysPost::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysPost::getPostName, keyword.trim())
                    .or().like(SysPost::getPostCode, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(SysPost::getStatus, status);
        }
        Page<SysPost> page = postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public SysPost get(Long id) {
        SysPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "岗位不存在");
        }
        return post;
    }

    @Transactional
    public SysPost create(SysPost request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        // 前端不会送 postCode：未带编码时自动生成 P + 序号，避免 NOT NULL 唯一列缺省导致 500。
        String code = StringUtils.hasText(request.getPostCode())
                ? request.getPostCode().trim() : generateCode();
        request.setPostCode(code);
        validate(request);
        Long userId = SecurityUtils.currentUserIdOrNull();
        Integer orderNum = request.getOrderNum() == null ? 0 : request.getOrderNum();
        Integer status = request.getStatus() == null ? 1 : request.getStatus();
        checkUnique(code, request.getPostName().trim(), null);
        // 编码/名称已被逻辑删除的历史行占用时，恢复该行（重复调用保持幂等，不冒 500）。
        SysPost deletedRow = postMapper.selectByCodeIncludeDeleted(code);
        if (deletedRow != null) {
            postMapper.revive(deletedRow.getId(), request.getPostName().trim(), orderNum, status,
                    request.getRemark(), userId);
            changeLogService.record("sys_post", deletedRow.getId(), null, request);
            return get(deletedRow.getId());
        }
        SysPost post = new SysPost();
        post.setPostCode(code);
        post.setPostName(request.getPostName().trim());
        post.setOrderNum(orderNum);
        post.setStatus(status);
        post.setRemark(request.getRemark());
        postMapper.insert(post);
        changeLogService.record("sys_post", post.getId(), null, post);
        return post;
    }

    private String generateCode() {
        long next = postMapper.selectMaxAutoCode() + 1;
        while (next < 1000000L) {
            String candidate = String.format("P%03d", next);
            SysPost existing = postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                    .eq(SysPost::getPostCode, candidate).last("LIMIT 1"));
            if (existing == null) {
                return candidate;
            }
            next++;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法生成岗位编码");
    }

    @Transactional
    public SysPost update(Long id, SysPost request) {
        SysPost existing = get(id);
        SysPost before = copyOf(existing);
        // PUT 为局部更新：未传字段沿用原值，合并后再校验，避免前端只提交变更字段时报必填错误。
        SysPost merged = new SysPost();
        merged.setId(existing.getId());
        merged.setPostCode(StringUtils.hasText(request == null ? null : request.getPostCode())
                ? request.getPostCode().trim() : existing.getPostCode());
        merged.setPostName(StringUtils.hasText(request == null ? null : request.getPostName())
                ? request.getPostName().trim() : existing.getPostName());
        merged.setOrderNum(request != null && request.getOrderNum() != null
                ? request.getOrderNum() : existing.getOrderNum());
        merged.setStatus(request != null && request.getStatus() != null
                ? request.getStatus() : existing.getStatus());
        merged.setRemark(request != null && request.getRemark() != null
                ? request.getRemark() : existing.getRemark());
        validate(merged);
        checkUnique(merged.getPostCode(), merged.getPostName(), id);
        postMapper.updateById(merged);
        changeLogService.record("sys_post", id, before, merged);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        SysPost post = get(id);
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPostId, id));
        if (users > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "岗位下存在用户，不能删除");
        }
        postMapper.deleteById(id);
        changeLogService.record("sys_post", id, post, null);
    }

    private void validate(SysPost request) {
        if (request == null || !StringUtils.hasText(request.getPostCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位编码不能为空");
        }
        if (!StringUtils.hasText(request.getPostName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位名称不能为空");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位状态不合法");
        }
    }

    private void checkUnique(String postCode, String postName, Long excludeId) {
        SysPost byCode = postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, postCode).last("LIMIT 1"));
        if (byCode != null && !byCode.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位编码已存在");
        }
        SysPost byName = postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostName, postName).last("LIMIT 1"));
        if (byName != null && !byName.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位名称已存在");
        }
    }

    private SysPost copyOf(SysPost source) {
        SysPost copy = new SysPost();
        copy.setId(source.getId());
        copy.setPostCode(source.getPostCode());
        copy.setPostName(source.getPostName());
        copy.setOrderNum(source.getOrderNum());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
