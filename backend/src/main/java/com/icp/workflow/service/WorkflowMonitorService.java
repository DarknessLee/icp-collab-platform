package com.icp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.PageResult;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
import com.icp.workflow.support.WorkflowConstants;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程监控（docs/03 §5）：运行中 / 已完成 / 已驳回 / 超时统计与列表，
 * 超时在查询时按 workflow.timeout.hours 计算（调度扫描在 M2e）。
 */
@Service
public class WorkflowMonitorService {

    private final WfBusinessLinkMapper linkMapper;
    private final WorkflowInstanceService instanceService;

    public WorkflowMonitorService(WfBusinessLinkMapper linkMapper, WorkflowInstanceService instanceService) {
        this.linkMapper = linkMapper;
        this.instanceService = instanceService;
    }

    public Map<String, Object> stats(Integer timeoutHoursOverride) {
        long running = countByStatus(WorkflowConstants.LINK_RUNNING);
        long approved = countByStatus(WorkflowConstants.LINK_APPROVED);
        long rejected = countByStatus(WorkflowConstants.LINK_REJECTED);
        List<WfBusinessLink> runningRows = linkMapper.selectList(new LambdaQueryWrapper<WfBusinessLink>()
                .eq(WfBusinessLink::getStatus, WorkflowConstants.LINK_RUNNING));
        int timeoutHours = timeoutHoursOverride == null ? (int) instanceService.timeoutHours() : timeoutHoursOverride;
        long overdue = runningRows.stream().filter(row -> instanceService.isOverdue(row, timeoutHours)).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("running", running);
        data.put("completed", approved);
        data.put("rejected", rejected);
        data.put("total", running + approved + rejected);
        data.put("overdue", overdue);
        data.put("timeoutHours", timeoutHours);
        return data;
    }

    public PageResult<Map<String, Object>> monitor(int pageNum, int pageSize, String status,
                                                   Boolean overdue, String processKey, String keyword,
                                                   Integer timeoutHoursOverride) {
        int timeoutHours = timeoutHoursOverride == null ? (int) instanceService.timeoutHours() : timeoutHoursOverride;
        Integer linkStatus = statusOf(status);
        LambdaQueryWrapper<WfBusinessLink> wrapper = new LambdaQueryWrapper<>();
        if (linkStatus != null) {
            wrapper.eq(WfBusinessLink::getStatus, linkStatus);
        }
        if (processKey != null && !processKey.isBlank()) {
            wrapper.eq(WfBusinessLink::getProcessKey, processKey);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WfBusinessLink::getTitle, keyword).or().like(WfBusinessLink::getBusinessNo, keyword));
        }
        wrapper.orderByDesc(WfBusinessLink::getStartTime);
        List<WfBusinessLink> rows = linkMapper.selectList(wrapper);
        List<Map<String, Object>> items = new ArrayList<>();
        for (WfBusinessLink row : rows) {
            boolean isOverdue = instanceService.isOverdue(row, timeoutHours);
            if (overdue != null && overdue != isOverdue) {
                continue;
            }
            items.add(instanceService.linkItem(row));
        }
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(items.size(), from + pageSize);
        List<Map<String, Object>> pageItems = from >= items.size() ? List.of() : items.subList(from, to);
        return new PageResult<>(new ArrayList<>(pageItems), items.size(), pageNum, pageSize);
    }

    private Integer statusOf(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        switch (status) {
            case "running":
                return WorkflowConstants.LINK_RUNNING;
            case "completed":
                return WorkflowConstants.LINK_APPROVED;
            case "rejected":
                return WorkflowConstants.LINK_REJECTED;
            case "withdrawn":
                return WorkflowConstants.LINK_WITHDRAWN;
            default:
                return null;
        }
    }

    private long countByStatus(int status) {
        return linkMapper.selectCount(new LambdaQueryWrapper<WfBusinessLink>()
                .eq(WfBusinessLink::getStatus, status));
    }
}
