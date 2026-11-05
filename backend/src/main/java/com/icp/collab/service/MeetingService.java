package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollMeeting;
import com.icp.collab.mapper.CollMeetingMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.support.CodeSeq;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 会议纪要（W3-a）：状态 0待召开 / 1已召开 / 2已取消。
 * <p>数据权限：ADMIN / ICP_MANAGER / AUDITOR / MANAGEMENT（data_scope=4）可见全部；
 * DEPT_MANAGER 可见本部门及下级；EMPLOYEE 仅可见自己组织的会议
 * （attendee 为逗号分隔姓名文本，按姓名匹配成本高，本期只按 organizer 过滤，已在交付说明中注明）。</p>
 */
@Service
public class MeetingService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_HELD = 1;
    public static final int STATUS_CANCELLED = 2;

    private static final Set<String> MEETING_TYPES = Set.of("0", "1", "2", "3");

    private final CollMeetingMapper meetingMapper;
    private final CollabLookupService lookup;
    private final DataScopeService dataScopeService;

    public MeetingService(CollMeetingMapper meetingMapper,
                          CollabLookupService lookup,
                          DataScopeService dataScopeService) {
        this.meetingMapper = meetingMapper;
        this.lookup = lookup;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<CollMeeting> page(long pageNum, long pageSize, String keyword, String meetingType,
                                        Integer status, LocalDate dateFrom, LocalDate dateTo) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<CollMeeting> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, info);
        if (StringUtils.hasText(meetingType)) {
            wrapper.eq(CollMeeting::getMeetingType, meetingType);
        }
        if (status != null) {
            wrapper.eq(CollMeeting::getStatus, status);
        }
        if (dateFrom != null) {
            wrapper.ge(CollMeeting::getStartTime, dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            wrapper.lt(CollMeeting::getStartTime, dateTo.plusDays(1).atStartOfDay());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollMeeting::getTitle, keyword)
                    .or().like(CollMeeting::getMeetingNo, keyword)
                    .or().like(CollMeeting::getLocation, keyword)
                    .or().like(CollMeeting::getOrganizerName, keyword)
                    .or().like(CollMeeting::getAttendeeNames, keyword));
        }
        wrapper.orderByDesc(CollMeeting::getStartTime).orderByDesc(CollMeeting::getId);
        Page<CollMeeting> page = meetingMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public CollMeeting get(Long id) {
        CollMeeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会议不存在");
        }
        requireVisible(meeting);
        return meeting;
    }

    @Transactional
    public CollMeeting create(CollabRequests.MeetingCreate request) {
        String title = requireText(request.title(), "会议标题不能为空", 200);
        String meetingType = normalizeType(request.meetingType());
        if (request.startTime() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会议开始时间不能为空");
        }
        validateTimeRange(request.startTime(), request.endTime());

        Long me = lookup.currentUserId();
        Long organizerId = request.organizerId() == null ? me : request.organizerId();
        lookup.requireUser(organizerId);
        String organizerName = StringUtils.hasText(request.organizerName())
                ? request.organizerName().trim() : lookup.userName(organizerId);
        if (!StringUtils.hasText(organizerName)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会议组织者姓名不能为空");
        }
        Long deptId = request.deptId() == null ? lookup.userDeptId(organizerId) : request.deptId();
        String deptName = StringUtils.hasText(request.deptName())
                ? request.deptName().trim() : lookup.deptName(deptId);
        String attendeeNames = normalizeAttendees(request.attendeeNames());
        Integer attendeeCount = request.attendeeCount() == null
                ? countAttendees(attendeeNames) : request.attendeeCount();
        if (attendeeCount == null) {
            attendeeCount = 0;
        }
        String minutes = trimToNull(request.minutes());

        CollMeeting meeting = new CollMeeting();
        meeting.setMeetingNo(CodeSeq.next(meetingMapper.selectMaxNo(), "HY", 3));
        meeting.setTitle(title);
        meeting.setMeetingType(meetingType);
        meeting.setStartTime(request.startTime());
        meeting.setEndTime(request.endTime());
        meeting.setLocation(trimToNull(request.location()));
        meeting.setOrganizerId(organizerId);
        meeting.setOrganizerName(organizerName);
        meeting.setDeptId(deptId);
        meeting.setDeptName(deptName);
        meeting.setAttendeeNames(attendeeNames);
        meeting.setAttendeeCount(attendeeCount);
        meeting.setMinutes(minutes);
        meeting.setStatus(StringUtils.hasText(minutes) ? STATUS_HELD : STATUS_PENDING);
        meeting.setRemark(trimToNull(request.remark()));
        meetingMapper.insert(meeting);
        return meeting;
    }

    @Transactional
    public CollMeeting update(Long id, CollabRequests.MeetingUpdate request) {
        CollMeeting meeting = get(id);
        if (meeting.getStatus() != null && meeting.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已取消的会议不可编辑");
        }
        if (request.title() != null) {
            meeting.setTitle(requireText(request.title(), "会议标题不能为空", 200));
        }
        if (request.meetingType() != null) {
            meeting.setMeetingType(normalizeType(request.meetingType()));
        }
        if (request.startTime() != null) {
            meeting.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            meeting.setEndTime(request.endTime());
        }
        validateTimeRange(meeting.getStartTime(), meeting.getEndTime());
        if (request.location() != null) {
            meeting.setLocation(trimToNull(request.location()));
        }
        if (request.organizerId() != null) {
            lookup.requireUser(request.organizerId());
            meeting.setOrganizerId(request.organizerId());
            meeting.setOrganizerName(StringUtils.hasText(request.organizerName())
                    ? request.organizerName().trim() : lookup.userName(request.organizerId()));
        } else if (StringUtils.hasText(request.organizerName())) {
            meeting.setOrganizerName(request.organizerName().trim());
        }
        if (request.deptId() != null) {
            meeting.setDeptId(request.deptId());
            meeting.setDeptName(StringUtils.hasText(request.deptName())
                    ? request.deptName().trim() : lookup.deptName(request.deptId()));
        } else if (StringUtils.hasText(request.deptName())) {
            meeting.setDeptName(request.deptName().trim());
        }
        if (request.attendeeNames() != null) {
            meeting.setAttendeeNames(normalizeAttendees(request.attendeeNames()));
            meeting.setAttendeeCount(request.attendeeCount() == null
                    ? countAttendees(meeting.getAttendeeNames()) : request.attendeeCount());
        } else if (request.attendeeCount() != null) {
            meeting.setAttendeeCount(request.attendeeCount());
        }
        if (request.remark() != null) {
            meeting.setRemark(trimToNull(request.remark()));
        }
        meetingMapper.updateById(meeting);
        return meeting;
    }

    @Transactional
    public void delete(Long id) {
        CollMeeting meeting = get(id);
        Long me = lookup.currentUserId();
        if (!lookup.isAdmin() && !Objects.equals(meeting.getOrganizerId(), me)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅会议组织者或管理员可删除");
        }
        meetingMapper.deleteById(id);
    }

    /** 录入会议纪要：正文必填，状态置为已召开；已取消会议不可录入。 */
    @Transactional
    public CollMeeting writeMinutes(Long id, CollabRequests.MeetingMinutes request) {
        String minutes = requireText(request.minutes(), "会议纪要内容不能为空", 10000);
        CollMeeting meeting = get(id);
        if (meeting.getStatus() != null && meeting.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已取消的会议不可录入纪要");
        }
        meeting.setMinutes(minutes);
        meeting.setStatus(STATUS_HELD);
        meetingMapper.updateById(meeting);
        return meeting;
    }

    private void requireVisible(CollMeeting meeting) {
        DataScopeInfo info = dataScopeService.current();
        if (!dataScopeService.canAccess(info, meeting.getDeptId(), meeting.getOrganizerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限访问该记录");
        }
    }

    private void applyScope(LambdaQueryWrapper<CollMeeting> wrapper, DataScopeInfo info) {
        if (info.isFull()) {
            return;
        }
        if (info.isSelfOnly()) {
            wrapper.eq(CollMeeting::getOrganizerId, info.getUserId());
            return;
        }
        List<Long> deptIds = info.getDeptIds();
        Long me = info.getUserId();
        if (deptIds == null || deptIds.isEmpty()) {
            wrapper.eq(CollMeeting::getOrganizerId, me);
            return;
        }
        wrapper.and(w -> w.in(CollMeeting::getDeptId, deptIds)
                .or().eq(CollMeeting::getOrganizerId, me));
    }

    private static String normalizeType(String type) {
        String value = StringUtils.hasText(type) ? type.trim() : "0";
        if (!MEETING_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会议类型取值不合法（0周例会/1专题会/2评审会/3培训会）");
        }
        return value;
    }

    private static void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会议开始时间不能为空");
        }
        if (end != null && !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会议结束时间必须晚于开始时间");
        }
    }

    private static String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message + "（长度不能超过 " + maxLength + " 字符）");
        }
        return text;
    }

    private static String normalizeAttendees(String names) {
        if (!StringUtils.hasText(names)) {
            return null;
        }
        String text = names.trim();
        if (text.length() > 500) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参会人姓名长度不能超过 500 字符");
        }
        return text;
    }

    private static int countAttendees(String names) {
        if (!StringUtils.hasText(names)) {
            return 0;
        }
        return names.split("[,，、;；]").length;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
