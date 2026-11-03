package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScope;
import com.icp.common.datascope.DataScopeContext;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.system.dto.Requests;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysPost;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.entity.SysUserRole;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysPostMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import com.icp.system.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理：分页（数据权限过滤）/详情/增改删/状态/重置密码/角色分配/个人资料。
 * 单条越权统一 40300；密码只入不出（实体 password 为 WRITE_ONLY）。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final OnlineSessionService onlineSessionService;
    private final DataScopeService dataScopeService;
    private final ChangeLogService changeLogService;

    public UserService(SysUserMapper userMapper, SysDeptMapper deptMapper, SysPostMapper postMapper,
                       SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder,
                       PasswordPolicyService passwordPolicyService, OnlineSessionService onlineSessionService,
                       DataScopeService dataScopeService, ChangeLogService changeLogService) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.postMapper = postMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.onlineSessionService = onlineSessionService;
        this.dataScopeService = dataScopeService;
        this.changeLogService = changeLogService;
    }

    @DataScope("user")
    public PageResult<SysUser> page(long pageNum, long pageSize, String keyword, Integer status, Long deptId) {
        DataScopeInfo scope = DataScopeContext.require();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(query -> query.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getRealName, kw)
                    .or().like(SysUser::getMobile, kw));
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(SysUser::getDeptId, deptId);
        }
        if (!scope.isFull()) {
            if (scope.isSelfOnly()) {
                wrapper.eq(SysUser::getId, scope.getUserId());
            } else if (scope.getDeptIds().isEmpty()) {
                wrapper.eq(SysUser::getId, -1L);
            } else {
                wrapper.in(SysUser::getDeptId, scope.getDeptIds());
            }
        }
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillRelations(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @DataScope("user")
    public SysUser detail(Long id) {
        SysUser user = requireUser(id);
        DataScopeInfo scope = DataScopeContext.require();
        dataScopeService.checkAccess(scope, user.getDeptId(), user.getId());
        fillRelations(List.of(user));
        return user;
    }

    @Transactional
    public SysUser create(SysUser request) {
        if (request == null || !StringUtils.hasText(request.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名不能为空");
        }
        String username = request.getUsername().trim();
        if (username.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名不能超过 50 个字符");
        }
        checkUsernameUnique(username, null);
        if (!StringUtils.hasText(request.getRealName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "姓名不能为空");
        }
        validateDeptAndPost(request.getDeptId(), request.getPostId());
        validateRoles(request.getRoleIds());
        String rawPassword = StringUtils.hasText(request.getPassword()) ? request.getPassword() : "Icp@2026";
        passwordPolicyService.validate(rawPassword);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRealName(request.getRealName().trim());
        user.setDeptId(request.getDeptId());
        user.setPostId(request.getPostId());
        user.setGender(request.getGender());
        user.setMobile(trimToNull(request.getMobile()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setAvatar(trimToNull(request.getAvatar()));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setRemark(request.getRemark());
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setFailCount(0);
        // 用户名唯一索引覆盖逻辑删除行：历史行存在时复用（恢复）该行，保证新增接口幂等且不冒 500
        SysUser deleted = userMapper.selectByUsernameIncludeDeleted(username);
        if (deleted != null) {
            user.setId(deleted.getId());
            userMapper.reviveUser(user, SecurityUtils.currentUserIdOrNull());
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, deleted.getId()));
            bindRoles(deleted.getId(), request.getRoleIds());
            changeLogService.record("sys_user", deleted.getId(), null, user);
            return detailInternal(deleted.getId());
        }
        userMapper.insert(user);
        bindRoles(user.getId(), request.getRoleIds());
        changeLogService.record("sys_user", user.getId(), null, user);
        return detailInternal(user.getId());
    }

    @Transactional
    public SysUser update(Long id, SysUser request) {
        SysUser existing = requireUser(id);
        checkManagePermission(existing);
        SysUser before = copyOf(existing);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (StringUtils.hasText(request.getUsername())) {
            String username = request.getUsername().trim();
            if (!username.equals(existing.getUsername())) {
                checkUsernameUnique(username, id);
                existing.setUsername(username);
            }
        }
        if (StringUtils.hasText(request.getRealName())) {
            existing.setRealName(request.getRealName().trim());
        }
        if (request.getDeptId() != null) {
            validateDeptAndPost(request.getDeptId(), null);
            existing.setDeptId(request.getDeptId());
        }
        if (request.getPostId() != null) {
            validateDeptAndPost(null, request.getPostId());
            existing.setPostId(request.getPostId());
        }
        if (request.getGender() != null) {
            existing.setGender(request.getGender());
        }
        if (request.getMobile() != null) {
            existing.setMobile(trimToNull(request.getMobile()));
        }
        if (request.getEmail() != null) {
            existing.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getAvatar() != null) {
            existing.setAvatar(trimToNull(request.getAvatar()));
        }
        if (request.getStatus() != null) {
            validateStatus(request.getStatus());
            existing.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        userMapper.updateById(existing);
        if (request.getRoleIds() != null) {
            validateRoles(request.getRoleIds());
            bindRoles(id, request.getRoleIds());
        }
        if (existing.getStatus() != null && existing.getStatus() != 1) {
            onlineSessionService.revokeByUser(id);
        }
        changeLogService.record("sys_user", id, before, existing);
        return detailInternal(id);
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = requireUser(id);
        checkManagePermission(user);
        if (id.equals(SecurityUtils.currentUserIdOrNull())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "不能删除当前登录用户");
        }
        userMapper.deleteById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        onlineSessionService.revokeByUser(id);
        changeLogService.record("sys_user", id, user, null);
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        validateStatus(status);
        SysUser user = requireUser(id);
        checkManagePermission(user);
        if (id.equals(SecurityUtils.currentUserIdOrNull()) && status != 1) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "不能停用当前登录用户");
        }
        SysUser before = copyOf(user);
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, status)
                .set(SysUser::getUpdateTime, LocalDateTime.now());
        userMapper.update(null, update);
        if (status != 1) {
            onlineSessionService.revokeByUser(id);
        }
        changeLogService.record("sys_user", id, before, userMapper.selectById(id));
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        SysUser user = requireUser(id);
        checkManagePermission(user);
        passwordPolicyService.validate(password);
        String encoded = passwordEncoder.encode(password);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPassword, encoded)
                .set(SysUser::getPwdUpdateTime, LocalDateTime.now())
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLockUntil, null)
                .set(SysUser::getUpdateTime, LocalDateTime.now()));
        onlineSessionService.revokeByUser(id);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("resetPassword", true);
        changeLogService.record("sys_user", id, null, change);
    }

    @Transactional
    public void updateRoles(Long id, List<Long> roleIds) {
        SysUser user = requireUser(id);
        checkManagePermission(user);
        validateRoles(roleIds);
        bindRoles(id, roleIds);
        changeLogService.record("sys_user_role", id, null, Map.of("roleIds", roleIds));
    }

    public SysUser profile() {
        Long userId = SecurityUtils.currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = requireUser(userId);
        fillRelations(List.of(user));
        return user;
    }

    @Transactional
    public SysUser updateProfile(Requests.Profile request) {
        Long userId = SecurityUtils.currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = requireUser(userId);
        SysUser before = copyOf(user);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (request.realName() != null) {
            if (!StringUtils.hasText(request.realName())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "姓名不能为空");
            }
            user.setRealName(request.realName().trim());
        }
        if (request.email() != null) {
            user.setEmail(trimToNull(request.email()));
        }
        if (request.mobile() != null) {
            user.setMobile(trimToNull(request.mobile()));
        }
        userMapper.updateById(user);
        changeLogService.record("sys_user", userId, before, user);
        return detailInternal(userId);
    }

    private SysUser detailInternal(Long id) {
        SysUser user = requireUser(id);
        fillRelations(List.of(user));
        return user;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void checkManagePermission(SysUser target) {
        DataScopeInfo scope = dataScopeService.current();
        dataScopeService.checkAccess(scope, target.getDeptId(), target.getId());
    }

    private void fillRelations(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Set<Long> deptIds = users.stream().map(SysUser::getDeptId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> postIds = users.stream().map(SysUser::getPostId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (SysDept dept : deptMapper.selectBatchIds(deptIds)) {
                deptNames.put(dept.getId(), dept.getDeptName());
            }
        }
        Map<Long, String> postNames = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (SysPost post : postMapper.selectBatchIds(postIds)) {
                postNames.put(post.getId(), post.getPostName());
            }
        }
        for (SysUser user : users) {
            user.setDeptName(deptNames.get(user.getDeptId()));
            user.setPostName(postNames.get(user.getPostId()));
            List<SysRole> roles = roleMapper.selectByUserId(user.getId());
            user.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
            user.setRoleNames(roles.stream().map(SysRole::getRoleName).collect(Collectors.toList()));
        }
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    private void validateRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != new ArrayList<>(new java.util.LinkedHashSet<>(roleIds)).size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在");
        }
    }

    private void validateDeptAndPost(Long deptId, Long postId) {
        if (deptId != null && deptMapper.selectById(deptId) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门不存在");
        }
        if (postId != null && postMapper.selectById(postId) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "岗位不存在");
        }
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username).last("LIMIT 1"));
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户状态不合法");
        }
    }

    private SysUser copyOf(SysUser source) {
        SysUser copy = new SysUser();
        copy.setId(source.getId());
        copy.setUsername(source.getUsername());
        copy.setRealName(source.getRealName());
        copy.setDeptId(source.getDeptId());
        copy.setPostId(source.getPostId());
        copy.setGender(source.getGender());
        copy.setMobile(source.getMobile());
        copy.setEmail(source.getEmail());
        copy.setAvatar(source.getAvatar());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
