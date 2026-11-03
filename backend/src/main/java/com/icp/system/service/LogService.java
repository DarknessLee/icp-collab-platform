package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.system.entity.SysLoginLog;
import com.icp.system.entity.SysOperLog;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysLoginLogMapper;
import com.icp.system.mapper.SysOperLogMapper;
import com.icp.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 日志查询：操作日志 / 登录日志 / 变更日志（sys_oper_log 中 title=CHANGE）。
 * 查询结果按当前用户数据范围过滤，审计员（数据范围 4）可见全部。
 */
@Service
public class LogService {

    private static final String CHANGE_TITLE = "CHANGE";

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysUserMapper userMapper;
    private final DataScopeService dataScopeService;

    public LogService(SysOperLogMapper operLogMapper, SysLoginLogMapper loginLogMapper,
                      SysUserMapper userMapper, DataScopeService dataScopeService) {
        this.operLogMapper = operLogMapper;
        this.loginLogMapper = loginLogMapper;
        this.userMapper = userMapper;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<SysOperLog> pageOper(long pageNum, long pageSize, String operName, String module,
                                           Integer status, String startDate, String endDate) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .ne(SysOperLog::getTitle, CHANGE_TITLE)
                .orderByDesc(SysOperLog::getOperTime)
                .orderByDesc(SysOperLog::getId);
        if (StringUtils.hasText(operName)) {
            wrapper.like(SysOperLog::getOperName, operName.trim());
        }
        if (StringUtils.hasText(module)) {
            wrapper.and(query -> query.like(SysOperLog::getTitle, module.trim())
                    .or().like(SysOperLog::getBusinessType, module.trim())
                    .or().like(SysOperLog::getRequestUri, module.trim()));
        }
        if (status != null) {
            wrapper.eq(SysOperLog::getStatus, status);
        }
        applyTimeRange(wrapper, startDate, endDate);
        applyOperScope(wrapper);
        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public PageResult<SysLoginLog> pageLogin(long pageNum, long pageSize, String username,
                                             Integer status, String startDate, String endDate) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .orderByDesc(SysLoginLog::getLoginTime)
                .orderByDesc(SysLoginLog::getId);
        if (StringUtils.hasText(username)) {
            wrapper.like(SysLoginLog::getUsername, username.trim());
        }
        if (status != null) {
            wrapper.eq(SysLoginLog::getStatus, status);
        }
        applyLoginTimeRange(wrapper, startDate, endDate);
        DataScopeInfo scope = dataScopeService.current();
        if (scope.isSelfOnly()) {
            wrapper.eq(SysLoginLog::getUserId, scope.getUserId());
        } else if (scope.isDeptRestricted()) {
            List<Long> userIds = userIdsInDepts(scope.getDeptIds());
            if (userIds.isEmpty()) {
                wrapper.eq(SysLoginLog::getUserId, -1L);
            } else {
                wrapper.in(SysLoginLog::getUserId, userIds);
            }
        }
        Page<SysLoginLog> page = loginLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public PageResult<SysOperLog> pageChanges(long pageNum, long pageSize, String bizType, Long bizId,
                                              String startDate, String endDate) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getTitle, CHANGE_TITLE)
                .orderByDesc(SysOperLog::getOperTime)
                .orderByDesc(SysOperLog::getId);
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(SysOperLog::getBusinessType, bizType.trim());
        }
        if (bizId != null) {
            wrapper.apply("JSON_EXTRACT(request_param, '$.bizId') = {0}", bizId);
        }
        applyTimeRange(wrapper, startDate, endDate);
        applyOperScope(wrapper);
        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public List<SysOperLog> trace(String traceId) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getTraceId, traceId)
                .orderByAsc(SysOperLog::getId);
        applyOperScope(wrapper);
        return operLogMapper.selectList(wrapper);
    }

    private void applyOperScope(LambdaQueryWrapper<SysOperLog> wrapper) {
        DataScopeInfo scope = dataScopeService.current();
        if (scope.isFull()) {
            return;
        }
        if (scope.isSelfOnly()) {
            wrapper.eq(SysOperLog::getOperUserId, scope.getUserId());
        } else if (scope.isDeptRestricted()) {
            List<Long> deptIds = new ArrayList<>(scope.getDeptIds());
            List<Long> userIds = userIdsInDepts(deptIds);
            if (userIds.isEmpty()) {
                wrapper.in(SysOperLog::getDeptId, deptIds);
            } else {
                wrapper.and(query -> query.in(SysOperLog::getDeptId, deptIds)
                        .or().in(SysOperLog::getOperUserId, userIds));
            }
        }
    }

    private List<Long> userIdsInDepts(List<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return new ArrayList<>();
        }
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getDeptId, deptIds))
                .stream().map(SysUser::getId).collect(Collectors.toList());
    }

    private void applyTimeRange(LambdaQueryWrapper<SysOperLog> wrapper, String startDate, String endDate) {
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(SysOperLog::getOperTime, normalizeStart(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(SysOperLog::getOperTime, normalizeEnd(endDate));
        }
    }

    private void applyLoginTimeRange(LambdaQueryWrapper<SysLoginLog> wrapper, String startDate, String endDate) {
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(SysLoginLog::getLoginTime, normalizeStart(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(SysLoginLog::getLoginTime, normalizeEnd(endDate));
        }
    }

    private String normalizeStart(String date) {
        String value = date.trim();
        return value.length() <= 10 ? value + " 00:00:00" : value;
    }

    private String normalizeEnd(String date) {
        String value = date.trim();
        return value.length() <= 10 ? value + " 23:59:59" : value;
    }
}
