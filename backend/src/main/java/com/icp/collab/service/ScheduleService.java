package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollSchedule;
import com.icp.collab.mapper.CollScheduleMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 日程/会议（M2d / docs/03 §6.1 rule 9 + Lead D）：类型 1会议/2提醒/3日程；
 * 状态 0待开始/1进行中/2已结束/3已取消；跨部门会议通过参与人可见。
 */
@Service
public class ScheduleService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ONGOING = 1;
    public static final int STATUS_FINISHED = 2;
    public static final int STATUS_CANCELLED = 3;

    private final CollScheduleMapper scheduleMapper;
    private final CollabLookupService lookup;
    private final DataScopeService dataScopeService;

    public ScheduleService(CollScheduleMapper scheduleMapper, CollabLookupService lookup,
                           DataScopeService dataScopeService) {
        this.scheduleMapper = scheduleMapper;
        this.lookup = lookup;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<CollSchedule> page(long pageNum, long pageSize, String month, Integer scheduleType,
                                         Integer status, String keyword) {
        LambdaQueryWrapper<CollSchedule> wrapper = new LambdaQueryWrapper<>();
        applyVisibility(wrapper);
        if (StringUtils.hasText(month)) {
            YearMonth yearMonth = parseMonth(month);
            wrapper.ge(CollSchedule::getStartTime, yearMonth.atDay(1).atStartOfDay())
                    .lt(CollSchedule::getStartTime, yearMonth.plusMonths(1).atDay(1).atStartOfDay());
        }
        if (scheduleType != null) {
            wrapper.eq(CollSchedule::getScheduleType, scheduleType);
        }
        if (status != null) {
            wrapper.eq(CollSchedule::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollSchedule::getScheduleTitle, keyword)
                    .or().like(CollSchedule::getLocation, keyword)
                    .or().like(CollSchedule::getDescription, keyword));
        }
        wrapper.orderByAsc(CollSchedule::getStartTime);
        Page<CollSchedule> page = scheduleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fill(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /** GET /schedules/calendar?month=yyyy-MM：返回日历所需精简字段。 */
    public Map<String, Object> calendar(String month) {
        YearMonth yearMonth = parseMonth(month);
        LambdaQueryWrapper<CollSchedule> wrapper = new LambdaQueryWrapper<>();
        applyVisibility(wrapper);
        wrapper.ge(CollSchedule::getStartTime, yearMonth.atDay(1).atStartOfDay())
                .lt(CollSchedule::getStartTime, yearMonth.plusMonths(1).atDay(1).atStartOfDay())
                .orderByAsc(CollSchedule::getStartTime);
        List<CollSchedule> rows = scheduleMapper.selectList(wrapper);
        List<Map<String, Object>> items = new ArrayList<>();
        for (CollSchedule row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("title", row.getScheduleTitle());
            item.put("start", row.getStartTime());
            item.put("end", row.getEndTime());
            item.put("type", row.getScheduleType());
            item.put("status", row.getStatus());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", yearMonth.toString());
        result.put("items", items);
        result.put("total", items.size());
        return result;
    }

    public CollSchedule get(Long id) {
        CollSchedule schedule = requireVisible(id);
        fill(List.of(schedule));
        return schedule;
    }

    @Transactional
    public CollSchedule create(CollabRequests.ScheduleCreate request) {
        Long me = lookup.currentUserId();
        CollSchedule schedule = new CollSchedule();
        schedule.setScheduleTitle(requireText(request.scheduleTitle(), "日程标题", 200));
        schedule.setScheduleType(request.scheduleType() == null ? 3 : request.scheduleType());
        schedule.setStartTime(Objects.requireNonNull(request.startTime(), "开始时间不能为空"));
        schedule.setEndTime(request.endTime());
        validateTimes(schedule.getStartTime(), schedule.getEndTime());
        schedule.setLocation(request.location());
        schedule.setParticipants(joinParticipantIds(request.participantIds()));
        schedule.setRemindMinutes(request.remindMinutes() == null ? 15 : request.remindMinutes());
        schedule.setCreatorId(me);
        schedule.setDeptId(request.deptId() != null ? request.deptId() : lookup.currentDeptId());
        schedule.setStatus(STATUS_PENDING);
        schedule.setDescription(request.description());
        schedule.setRemark(request.remark());
        scheduleMapper.insert(schedule);
        fill(List.of(schedule));
        return schedule;
    }

    @Transactional
    public CollSchedule update(Long id, CollabRequests.ScheduleUpdate request) {
        CollSchedule schedule = requireEditable(id);
        if (request.scheduleTitle() != null) {
            schedule.setScheduleTitle(requireText(request.scheduleTitle(), "日程标题", 200));
        }
        if (request.scheduleType() != null) {
            schedule.setScheduleType(request.scheduleType());
        }
        if (request.startTime() != null) {
            schedule.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            schedule.setEndTime(request.endTime());
        }
        validateTimes(schedule.getStartTime(), schedule.getEndTime());
        if (request.location() != null) {
            schedule.setLocation(request.location());
        }
        if (request.participantIds() != null) {
            schedule.setParticipants(joinParticipantIds(request.participantIds()));
        }
        if (request.remindMinutes() != null) {
            schedule.setRemindMinutes(request.remindMinutes());
        }
        if (request.deptId() != null) {
            schedule.setDeptId(request.deptId());
        }
        if (request.description() != null) {
            schedule.setDescription(request.description());
        }
        if (request.remark() != null) {
            schedule.setRemark(request.remark());
        }
        scheduleMapper.updateById(schedule);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        CollSchedule schedule = requireVisible(id);
        requireCreatorOrAdmin(schedule);
        scheduleMapper.deleteById(id);
    }

    /** POST /schedules/{id}/cancel：0/1 → 3（已取消）。 */
    @Transactional
    public Map<String, Object> cancel(Long id) {
        CollSchedule schedule = requireVisible(id);
        requireCreatorOrAdmin(schedule);
        if (!Objects.equals(schedule.getStatus(), STATUS_PENDING)
                && !Objects.equals(schedule.getStatus(), STATUS_ONGOING)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待开始或进行中的日程可取消");
        }
        schedule.setStatus(STATUS_CANCELLED);
        scheduleMapper.updateById(schedule);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", schedule.getId());
        result.put("status", schedule.getStatus());
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private void applyVisibility(LambdaQueryWrapper<CollSchedule> wrapper) {
        DataScopeInfo info = dataScopeService.current();
        if (info.isFull()) {
            return;
        }
        Long me = lookup.currentUserId();
        if (info.isSelfOnly()) {
            wrapper.and(w -> {
                w.eq(CollSchedule::getCreatorId, me);
                if (me != null) {
                    w.or().apply("FIND_IN_SET({0}, participants)", me);
                }
            });
        } else if (info.isDeptRestricted()) {
            Set<Long> deptUserIds = lookup.userIdsOfDepts(info.getDeptIds());
            wrapper.and(w -> {
                if (!deptUserIds.isEmpty()) {
                    w.in(CollSchedule::getCreatorId, deptUserIds);
                    w.or().in(CollSchedule::getDeptId, info.getDeptIds());
                } else {
                    w.in(CollSchedule::getDeptId, info.getDeptIds());
                }
                w.or().eq(CollSchedule::getCreatorId, me);
                w.or().apply("FIND_IN_SET({0}, participants)", me);
            });
        }
    }

    private CollSchedule requireVisible(Long id) {
        CollSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "日程不存在");
        }
        DataScopeInfo info = dataScopeService.current();
        Long me = lookup.currentUserId();
        boolean visible = info.isFull()
                || Objects.equals(schedule.getCreatorId(), me)
                || participantIds(schedule.getParticipants()).contains(me);
        if (!visible && info.isDeptRestricted()) {
            if (schedule.getDeptId() != null && info.getDeptIds().contains(schedule.getDeptId())) {
                visible = true;
            } else if (schedule.getCreatorId() != null) {
                Long creatorDept = lookup.userDeptId(schedule.getCreatorId());
                visible = creatorDept != null && info.getDeptIds().contains(creatorDept);
            }
        }
        if (!visible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限访问该记录");
        }
        return schedule;
    }

    private CollSchedule requireEditable(Long id) {
        CollSchedule schedule = requireVisible(id);
        requireCreatorOrAdmin(schedule);
        if (Objects.equals(schedule.getStatus(), STATUS_CANCELLED)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已取消的日程不可编辑");
        }
        return schedule;
    }

    private void requireCreatorOrAdmin(CollSchedule schedule) {
        if (!lookup.isAdmin() && !Objects.equals(schedule.getCreatorId(), lookup.currentUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅日程创建人可操作");
        }
    }

    private void fill(List<CollSchedule> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> creatorNames = lookup.userNames(rows.stream()
                .map(CollSchedule::getCreatorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Set<Long> participantIds = rows.stream()
                .flatMap(row -> participantIds(row.getParticipants()).stream())
                .collect(Collectors.toSet());
        Map<Long, String> participantNames = lookup.userNames(participantIds);
        for (CollSchedule row : rows) {
            row.setCreatorName(creatorNames.get(row.getCreatorId()));
            List<Long> ids = participantIds(row.getParticipants());
            row.setParticipantIds(ids);
            row.setParticipantNames(ids.stream().map(participantNames::get)
                    .filter(Objects::nonNull).toList());
        }
    }

    private List<Long> participantIds(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(value));
            } catch (NumberFormatException ignored) {
                // skip malformed participant id
            }
        }
        return ids;
    }

    private String joinParticipantIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        distinct.forEach(lookup::requireUser);
        return distinct.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (start == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始时间不能为空");
        }
        if (end != null && !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    private String requireText(String value, String label, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "不能为空");
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "长度不能超过 " + maxLength);
        }
        return text;
    }
}
