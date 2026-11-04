package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollNotice;
import com.icp.collab.entity.CollNoticeRead;
import com.icp.collab.mapper.CollNoticeMapper;
import com.icp.collab.mapper.CollNoticeReadMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告（M2d / docs/03 §6.1 rule 7 + Lead C）：
 * 状态 0草稿 / 1已发布 / 2已关闭；范围编码 dept_scope：全公司 / DEPT:2,3 / ROLE:EMPLOYEE,AUDITOR。
 * GET /notices 仅返回已发布且在范围内的公告；已读按 user 幂等。
 */
@Service
public class NoticeService {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_CLOSED = 2;

    public static final int SCOPE_ALL = 1;
    public static final int SCOPE_DEPT = 2;
    public static final int SCOPE_ROLE = 3;

    private final CollNoticeMapper noticeMapper;
    private final CollNoticeReadMapper readMapper;
    private final CollabLookupService lookup;

    public NoticeService(CollNoticeMapper noticeMapper, CollNoticeReadMapper readMapper,
                         CollabLookupService lookup) {
        this.noticeMapper = noticeMapper;
        this.readMapper = readMapper;
        this.lookup = lookup;
    }

    public PageResult<CollNotice> page(long pageNum, long pageSize, Integer noticeType, Integer status,
                                       String keyword) {
        Long me = lookup.currentUserId();
        boolean admin = lookup.isAdmin();
        LambdaQueryWrapper<CollNotice> wrapper = new LambdaQueryWrapper<>();
        if (noticeType != null) {
            wrapper.eq(CollNotice::getNoticeType, noticeType);
        }
        if (status != null) {
            wrapper.eq(CollNotice::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollNotice::getNoticeTitle, keyword)
                    .or().like(CollNotice::getContent, keyword));
        }
        wrapper.orderByDesc(CollNotice::getIsTop).orderByDesc(CollNotice::getPublishTime)
                .orderByDesc(CollNotice::getId);
        List<CollNotice> candidates = noticeMapper.selectList(wrapper);
        List<CollNotice> visible = candidates.stream()
                .filter(notice -> admin || Objects.equals(notice.getPublisherId(), me)
                        || (Objects.equals(notice.getStatus(), STATUS_PUBLISHED) && inScope(notice)))
                .collect(Collectors.toCollection(ArrayList::new));
        int total = visible.size();
        int from = (int) Math.max(0, (pageNum - 1) * pageSize);
        int to = (int) Math.min(total, from + pageSize);
        List<CollNotice> rows = from >= total ? List.of() : visible.subList(from, to);
        fill(rows);
        return new PageResult<>(rows, total, pageNum, pageSize);
    }

    public CollNotice get(Long id) {
        CollNotice notice = requireVisible(id);
        fill(List.of(notice));
        return notice;
    }

    @Transactional
    public CollNotice create(CollabRequests.NoticeCreate request) {
        CollNotice notice = new CollNotice();
        notice.setNoticeTitle(requireText(request.noticeTitle(), "公告标题", 200));
        notice.setNoticeType(request.noticeType() == null ? 2 : request.noticeType());
        notice.setContent(request.content());
        notice.setStatus(STATUS_DRAFT);
        notice.setPublisherId(lookup.currentUserId());
        notice.setIsTop(request.isTop() == null ? 0 : request.isTop());
        notice.setDeptScope(encodeScope(request.scope() == null ? SCOPE_ALL : request.scope(),
                request.scopeDeptIds(), request.scopeRoles()));
        notice.setRemark(request.remark());
        noticeMapper.insert(notice);
        fill(List.of(notice));
        return notice;
    }

    @Transactional
    public CollNotice update(Long id, CollabRequests.NoticeUpdate request) {
        CollNotice notice = requireEditable(id);
        if (request.noticeTitle() != null) {
            notice.setNoticeTitle(requireText(request.noticeTitle(), "公告标题", 200));
        }
        if (request.noticeType() != null) {
            notice.setNoticeType(request.noticeType());
        }
        if (request.content() != null) {
            notice.setContent(request.content());
        }
        if (request.isTop() != null) {
            notice.setIsTop(request.isTop());
        }
        if (request.scope() != null) {
            notice.setDeptScope(encodeScope(request.scope(), request.scopeDeptIds(), request.scopeRoles()));
        }
        if (request.remark() != null) {
            notice.setRemark(request.remark());
        }
        noticeMapper.updateById(notice);
        return get(id);
    }

    @Transactional
    public Map<String, Object> publish(Long id) {
        CollNotice notice = requireNotice(id);
        if (!Objects.equals(notice.getStatus(), STATUS_DRAFT)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿公告可发布");
        }
        requirePublisherOrAdmin(notice);
        notice.setStatus(STATUS_PUBLISHED);
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
        return stateOf(notice);
    }

    @Transactional
    public Map<String, Object> close(Long id) {
        CollNotice notice = requireNotice(id);
        if (!Objects.equals(notice.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅已发布公告可关闭");
        }
        requirePublisherOrAdmin(notice);
        notice.setStatus(STATUS_CLOSED);
        noticeMapper.updateById(notice);
        return stateOf(notice);
    }

    @Transactional
    public void delete(Long id) {
        CollNotice notice = requireNotice(id);
        if (!lookup.isAdmin()) {
            requirePublisherOrAdmin(notice);
            if (!Objects.equals(notice.getStatus(), STATUS_DRAFT)) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿公告可删除");
            }
        }
        noticeMapper.deleteById(id);
    }

    /** POST /notices/{id}/read：幂等标记已读。 */
    @Transactional
    public Map<String, Object> read(Long id) {
        requireVisible(id);
        Long me = lookup.currentUserId();
        CollNoticeRead record = new CollNoticeRead();
        record.setNoticeId(id);
        record.setUserId(me);
        record.setReadTime(LocalDateTime.now());
        readMapper.insertIgnore(record);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noticeId", id);
        result.put("read", true);
        result.put("readers", readMapper.countReaders(id));
        return result;
    }

    public Map<String, Object> unreadCount() {
        Long me = lookup.currentUserId();
        Set<Long> readIds = new LinkedHashSet<>(readMapper.selectReadNoticeIds(me));
        long unread = visiblePublished().stream().map(CollNotice::getId)
                .filter(id -> !readIds.contains(id)).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", unread);
        result.put("read", readIds.size());
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private List<CollNotice> visiblePublished() {
        return noticeMapper.selectList(new LambdaQueryWrapper<CollNotice>()
                        .eq(CollNotice::getStatus, STATUS_PUBLISHED)).stream()
                .filter(this::inScope).toList();
    }

    private CollNotice requireNotice(Long id) {
        CollNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return notice;
    }

    private CollNotice requireVisible(Long id) {
        CollNotice notice = requireNotice(id);
        Long me = lookup.currentUserId();
        if (lookup.isAdmin() || Objects.equals(notice.getPublisherId(), me)) {
            return notice;
        }
        if (!Objects.equals(notice.getStatus(), STATUS_PUBLISHED) || !inScope(notice)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无公告访问权限");
        }
        return notice;
    }

    private CollNotice requireEditable(Long id) {
        CollNotice notice = requireNotice(id);
        requirePublisherOrAdmin(notice);
        if (!Objects.equals(notice.getStatus(), STATUS_DRAFT)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿公告可编辑");
        }
        return notice;
    }

    private void requirePublisherOrAdmin(CollNotice notice) {
        if (!lookup.isAdmin() && !Objects.equals(notice.getPublisherId(), lookup.currentUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅公告发布人可操作");
        }
    }

    public boolean inScope(CollNotice notice) {
        String scope = notice.getDeptScope();
        if (!StringUtils.hasText(scope) || "全公司".equals(scope)) {
            return true;
        }
        if (scope.startsWith("DEPT:")) {
            Long deptId = lookup.currentDeptId();
            if (deptId == null) {
                return false;
            }
            return parseLongs(scope.substring(5)).contains(deptId);
        }
        if (scope.startsWith("ROLE:")) {
            Set<String> mine = new LinkedHashSet<>(lookup.currentRoles());
            return parseStrings(scope.substring(5)).stream().anyMatch(mine::contains);
        }
        return true;
    }

    private void fill(List<CollNotice> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> publisherNames = lookup.userNames(rows.stream()
                .map(CollNotice::getPublisherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Long me = lookup.currentUserId();
        Set<Long> readIds = new LinkedHashSet<>(readMapper.selectReadNoticeIds(me));
        for (CollNotice notice : rows) {
            notice.setPublisherName(publisherNames.get(notice.getPublisherId()));
            notice.setRead(readIds.contains(notice.getId()));
            applyScopeFields(notice);
        }
    }

    private void applyScopeFields(CollNotice notice) {
        String scope = notice.getDeptScope();
        if (!StringUtils.hasText(scope) || "全公司".equals(scope)) {
            notice.setScope(SCOPE_ALL);
            notice.setScopeDeptIds(List.of());
            notice.setScopeRoles(List.of());
        } else if (scope.startsWith("DEPT:")) {
            notice.setScope(SCOPE_DEPT);
            notice.setScopeDeptIds(parseLongs(scope.substring(5)));
            notice.setScopeRoles(List.of());
        } else if (scope.startsWith("ROLE:")) {
            notice.setScope(SCOPE_ROLE);
            notice.setScopeDeptIds(List.of());
            notice.setScopeRoles(parseStrings(scope.substring(5)));
        } else {
            notice.setScope(SCOPE_ALL);
        }
    }

    private String encodeScope(int scope, List<Long> deptIds, List<String> roles) {
        if (scope == SCOPE_DEPT) {
            if (deptIds == null || deptIds.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "按部门发布时需指定部门");
            }
            return "DEPT:" + deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        if (scope == SCOPE_ROLE) {
            if (roles == null || roles.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "按角色发布时需指定角色");
            }
            return "ROLE:" + roles.stream().map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }
        return "全公司";
    }

    private List<Long> parseLongs(String csv) {
        return parseStrings(csv).stream().map(Long::valueOf).toList();
    }

    private List<String> parseStrings(String csv) {
        List<String> result = new ArrayList<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, Object> stateOf(CollNotice notice) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", notice.getId());
        result.put("status", notice.getStatus());
        result.put("publishTime", notice.getPublishTime());
        return result;
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
