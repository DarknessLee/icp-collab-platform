package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollDoc;
import com.icp.collab.entity.CollDocBorrow;
import com.icp.collab.entity.CollDocPerm;
import com.icp.collab.mapper.CollBorrowMapper;
import com.icp.collab.mapper.CollDocCategoryMapper;
import com.icp.collab.mapper.CollDocMapper;
import com.icp.collab.mapper.CollDocPermMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.icp.support.CodeSeq;
import com.icp.system.entity.SysFile;
import com.icp.system.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档中心（M2d / docs/03 §6.1 rule 4/5 + D37）：
 * 状态 0草稿 → 1已发布 → 2已归档；可见性 = 已发布 + 授权命中，或本人文档，或借阅已通过兜底；
 * 下载顺序同可见性，另需 perm_type 含下载；授权 PUT 全量覆盖（physical delete 后插入）。
 */
@Service
public class DocService {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_ARCHIVED = 2;

    public static final int PERM_VIEW = 1;
    public static final int PERM_DOWNLOAD = 2;
    public static final int PERM_EDIT = 3;

    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)\\.(\\d+)$");

    private final CollDocMapper docMapper;
    private final CollDocPermMapper permMapper;
    private final CollBorrowMapper borrowMapper;
    private final CollDocCategoryMapper categoryMapper;
    private final CollabLookupService lookup;
    private final FileService fileService;

    public DocService(CollDocMapper docMapper,
                      CollDocPermMapper permMapper,
                      CollBorrowMapper borrowMapper,
                      CollDocCategoryMapper categoryMapper,
                      CollabLookupService lookup,
                      FileService fileService) {
        this.docMapper = docMapper;
        this.permMapper = permMapper;
        this.borrowMapper = borrowMapper;
        this.categoryMapper = categoryMapper;
        this.lookup = lookup;
        this.fileService = fileService;
    }

    public record DocFile(byte[] bytes, String fileName, String contentType) {
    }

    public PageResult<CollDoc> page(long pageNum, long pageSize, Long categoryId, Integer docType,
                                    Integer status, String keyword) {
        Long me = lookup.currentUserId();
        boolean admin = lookup.isAdmin();
        List<CollDocPerm> perms = myPerms();
        Set<Long> allowedIds = perms.stream().map(CollDocPerm::getDocId).collect(Collectors.toSet());
        Set<Long> borrowIds = approvedBorrowDocIds(me);

        LambdaQueryWrapper<CollDoc> wrapper = new LambdaQueryWrapper<>();
        if (!admin) {
            wrapper.and(w -> {
                w.eq(CollDoc::getOwnerUserId, me);
                if (!allowedIds.isEmpty()) {
                    w.or().in(CollDoc::getStatus, STATUS_PUBLISHED, STATUS_ARCHIVED).in(CollDoc::getId, allowedIds);
                }
                if (!borrowIds.isEmpty()) {
                    w.or().in(CollDoc::getId, borrowIds);
                }
            });
        }
        if (categoryId != null) {
            wrapper.eq(CollDoc::getCategoryId, categoryId);
        }
        if (docType != null) {
            wrapper.eq(CollDoc::getDocType, docType);
        }
        if (status != null) {
            wrapper.eq(CollDoc::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollDoc::getDocName, keyword)
                    .or().like(CollDoc::getDocNo, keyword)
                    .or().like(CollDoc::getDescription, keyword));
        }
        wrapper.orderByDesc(CollDoc::getId);
        Page<CollDoc> page = docMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fill(page.getRecords(), perms);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public CollDoc get(Long id) {
        CollDoc doc = requireDoc(id);
        if (!canView(doc, myPerms())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无文档访问权限");
        }
        fill(List.of(doc), myPerms());
        return doc;
    }

    @Transactional
    public CollDoc create(CollabRequests.DocCreate request) {
        Long me = lookup.currentUserId();
        CollDoc doc = new CollDoc();
        doc.setDocNo(CodeSeq.next(docMapper.selectMaxNo(), "WD", 4));
        doc.setDocName(requireName(request.docName()));
        doc.setCategoryId(requireCategory(request.categoryId()));
        doc.setDocType(request.docType() == null ? 5 : request.docType());
        doc.setVersion(StringUtils.hasText(request.version()) ? request.version().trim() : "V1.0");
        if (request.fileId() != null) {
            fileService.get(request.fileId());
            doc.setFileId(request.fileId());
        }
        doc.setDeptId(request.deptId() != null ? request.deptId() : lookup.currentDeptId());
        doc.setOwnerUserId(me);
        doc.setStatus(STATUS_DRAFT);
        doc.setDownloadCount(0);
        doc.setDescription(request.description());
        doc.setRemark(request.remark());
        docMapper.insert(doc);
        fill(List.of(doc), List.of());
        return doc;
    }

    @Transactional
    public CollDoc update(Long id, CollabRequests.DocUpdate request) {
        CollDoc doc = requireDoc(id);
        if (Objects.equals(doc.getStatus(), STATUS_ARCHIVED)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已归档文档不可编辑");
        }
        if (!canEdit(doc, myPerms())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无文档编辑权限");
        }
        if (request.docName() != null) {
            doc.setDocName(requireName(request.docName()));
        }
        if (request.categoryId() != null) {
            doc.setCategoryId(requireCategory(request.categoryId()));
        }
        if (request.docType() != null) {
            doc.setDocType(request.docType());
        }
        if (request.fileId() != null && !Objects.equals(request.fileId(), doc.getFileId())) {
            fileService.get(request.fileId());
            doc.setFileId(request.fileId());
            doc.setVersion(bumpVersion(doc.getVersion()));
        }
        if (request.deptId() != null) {
            doc.setDeptId(request.deptId());
        }
        if (request.description() != null) {
            doc.setDescription(request.description());
        }
        if (request.remark() != null) {
            doc.setRemark(request.remark());
        }
        docMapper.updateById(doc);
        return get(id);
    }

    @Transactional
    public Map<String, Object> publish(Long id) {
        CollDoc doc = requireDoc(id);
        if (!Objects.equals(doc.getStatus(), STATUS_DRAFT)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿文档可发布");
        }
        if (!canEdit(doc, myPerms())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无文档发布权限");
        }
        doc.setStatus(STATUS_PUBLISHED);
        docMapper.updateById(doc);
        return stateOf(doc);
    }

    @Transactional
    public Map<String, Object> archive(Long id) {
        CollDoc doc = requireDoc(id);
        if (!Objects.equals(doc.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅已发布文档可归档");
        }
        if (!canEdit(doc, myPerms())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无文档归档权限");
        }
        doc.setStatus(STATUS_ARCHIVED);
        docMapper.updateById(doc);
        return stateOf(doc);
    }

    @Transactional
    public void delete(Long id) {
        CollDoc doc = requireDoc(id);
        if (!lookup.isAdmin()) {
            if (!Objects.equals(doc.getOwnerUserId(), lookup.currentUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅文档创建人可删除文档");
            }
            if (!Objects.equals(doc.getStatus(), STATUS_DRAFT)) {
                throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿文档可删除");
            }
        }
        docMapper.deleteById(id);
    }

    public List<CollDocPerm> perms(Long docId) {
        CollDoc doc = requireDoc(docId);
        if (!lookup.isAdmin() && !Objects.equals(doc.getOwnerUserId(), lookup.currentUserId())
                && !canEdit(doc, myPerms())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看文档权限");
        }
        List<CollDocPerm> perms = permMapper.selectList(new LambdaQueryWrapper<CollDocPerm>()
                .eq(CollDocPerm::getDocId, docId).orderByAsc(CollDocPerm::getPermType)
                .orderByAsc(CollDocPerm::getTargetType).orderByAsc(CollDocPerm::getTargetId));
        fillPermNames(perms);
        return perms;
    }

    /** PUT /docs/{id}/perms：全量覆盖，物理删除旧行后插入（uk_doc_perm 不含 deleted）。 */
    @Transactional
    public List<CollDocPerm> replacePerms(Long docId, List<CollabRequests.PermItem> items) {
        CollDoc doc = requireDoc(docId);
        if (!lookup.isAdmin() && !Objects.equals(doc.getOwnerUserId(), lookup.currentUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅文档创建人可配置授权");
        }
        List<CollDocPerm> rows = new ArrayList<>();
        Set<String> dedup = new HashSet<>();
        if (items != null) {
            for (CollabRequests.PermItem item : items) {
                validatePermItem(item);
                String key = item.targetType() + "-" + item.targetId() + "-" + item.permType();
                if (!dedup.add(key)) {
                    continue;
                }
                CollDocPerm row = new CollDocPerm();
                row.setDocId(docId);
                row.setTargetType(item.targetType());
                row.setTargetId(item.targetId());
                row.setPermType(item.permType());
                rows.add(row);
            }
        }
        permMapper.deleteByDocId(docId);
        for (CollDocPerm row : rows) {
            permMapper.insert(row);
        }
        fillPermNames(rows);
        return rows;
    }

    /** GET /docs/{id}/download：已发布/归档 + 下载授权 或 借阅已通过；下载次数 +1。 */
    @Transactional
    public DocFile download(Long id) {
        CollDoc doc = requireDoc(id);
        Long me = lookup.currentUserId();
        boolean admin = lookup.isAdmin();
        boolean owner = Objects.equals(doc.getOwnerUserId(), me);
        if (!admin && !owner) {
            if (Objects.equals(doc.getStatus(), STATUS_DRAFT)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "草稿文档不可下载");
            }
            List<CollDocPerm> perms = myPerms();
            boolean granted = hasPerm(doc.getId(), perms, PERM_DOWNLOAD);
            boolean borrowed = approvedBorrowDocIds(me).contains(doc.getId());
            if (!granted && !borrowed) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无文档下载权限");
            }
        }
        if (doc.getFileId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档未关联文件");
        }
        SysFile file = fileService.get(doc.getFileId());
        byte[] bytes = fileService.loadBytes(file);
        doc.setDownloadCount((doc.getDownloadCount() == null ? 0 : doc.getDownloadCount()) + 1);
        docMapper.updateById(doc);
        return new DocFile(bytes, file.getOriginalName(), file.getContentType());
    }

    // ---------------------------------------------------------------- helpers

    private CollDoc requireDoc(Long id) {
        CollDoc doc = docMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    private boolean canView(CollDoc doc, List<CollDocPerm> perms) {
        Long me = lookup.currentUserId();
        if (lookup.isAdmin() || Objects.equals(doc.getOwnerUserId(), me)) {
            return true;
        }
        if (!Objects.equals(doc.getStatus(), STATUS_PUBLISHED) && !Objects.equals(doc.getStatus(), STATUS_ARCHIVED)) {
            return false;
        }
        return hasPerm(doc.getId(), perms, PERM_VIEW) || approvedBorrowDocIds(me).contains(doc.getId());
    }

    private boolean canEdit(CollDoc doc, List<CollDocPerm> perms) {
        Long me = lookup.currentUserId();
        if (lookup.isAdmin() || Objects.equals(doc.getOwnerUserId(), me)) {
            return true;
        }
        return hasPerm(doc.getId(), perms, PERM_EDIT);
    }

    private boolean hasPerm(Long docId, List<CollDocPerm> perms, int required) {
        for (CollDocPerm perm : perms) {
            if (!Objects.equals(perm.getDocId(), docId)) {
                continue;
            }
            if (perm.getPermType() != null && perm.getPermType() >= required) {
                return true;
            }
        }
        return false;
    }

    /** 当前用户命中的授权行：全员(target_type=0) / 当前角色(target_type=1) / 本人(2) / 本部门(3)。 */
    private List<CollDocPerm> myPerms() {
        Long me = lookup.currentUserId();
        List<Long> roleIds = lookup.currentRoleIds();
        Long deptId = lookup.currentDeptId();
        LambdaQueryWrapper<CollDocPerm> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            w.eq(CollDocPerm::getTargetType, 0);
            if (!roleIds.isEmpty()) {
                w.or(x -> x.eq(CollDocPerm::getTargetType, 1).in(CollDocPerm::getTargetId, roleIds));
            }
            w.or(x -> x.eq(CollDocPerm::getTargetType, 2).eq(CollDocPerm::getTargetId, me));
            if (deptId != null) {
                w.or(x -> x.eq(CollDocPerm::getTargetType, 3).eq(CollDocPerm::getTargetId, deptId));
            }
        });
        return permMapper.selectList(wrapper);
    }

    private Set<Long> approvedBorrowDocIds(Long userId) {
        List<CollDocBorrow> borrows = borrowMapper.selectList(new LambdaQueryWrapper<CollDocBorrow>()
                .eq(CollDocBorrow::getBorrowerId, userId)
                .eq(CollDocBorrow::getStatus, 1));
        return borrows.stream().map(CollDocBorrow::getDocId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void fill(List<CollDoc> docs, List<CollDocPerm> perms) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = docs.stream().map(CollDoc::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> userIds = docs.stream().map(CollDoc::getOwnerUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> deptIds = docs.stream().map(CollDoc::getDeptId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectBatchIds(categoryIds).forEach(c -> categoryNames.put(c.getId(), c.getCategoryName()));
        }
        Map<Long, String> userNames = lookup.userNames(userIds);
        Map<Long, String> deptNames = lookup.deptNames(deptIds);
        Set<Long> borrowIds = approvedBorrowDocIds(lookup.currentUserId());
        for (CollDoc doc : docs) {
            doc.setCategoryName(categoryNames.get(doc.getCategoryId()));
            doc.setOwnerName(userNames.get(doc.getOwnerUserId()));
            doc.setDeptName(deptNames.get(doc.getDeptId()));
            SysFile file = doc.getFileId() == null ? null : fileService.get(doc.getFileId());
            if (file != null) {
                doc.setFileName(file.getOriginalName());
                doc.setContentType(file.getContentType());
            }
            boolean owner = Objects.equals(doc.getOwnerUserId(), lookup.currentUserId());
            if (lookup.isAdmin() || owner) {
                doc.setCanView(true);
                doc.setCanDownload(true);
            } else if (!Objects.equals(doc.getStatus(), STATUS_PUBLISHED)
                    && !Objects.equals(doc.getStatus(), STATUS_ARCHIVED)) {
                doc.setCanView(false);
                doc.setCanDownload(false);
            } else {
                doc.setCanView(hasPerm(doc.getId(), perms, PERM_VIEW) || borrowIds.contains(doc.getId()));
                doc.setCanDownload(hasPerm(doc.getId(), perms, PERM_DOWNLOAD) || borrowIds.contains(doc.getId()));
            }
        }
    }

    private void fillPermNames(List<CollDocPerm> perms) {
        if (perms == null || perms.isEmpty()) {
            return;
        }
        Map<Long, String> roleNames = lookup.roleNames(perms.stream()
                .filter(p -> p.getTargetType() != null && p.getTargetType() == 1)
                .map(CollDocPerm::getTargetId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> userNames = lookup.userNames(perms.stream()
                .filter(p -> p.getTargetType() != null && p.getTargetType() == 2)
                .map(CollDocPerm::getTargetId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> deptNames = lookup.deptNames(perms.stream()
                .filter(p -> p.getTargetType() != null && p.getTargetType() == 3)
                .map(CollDocPerm::getTargetId).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (CollDocPerm perm : perms) {
            int type = perm.getTargetType() == null ? -1 : perm.getTargetType();
            perm.setTargetName(switch (type) {
                case 0 -> "全员";
                case 1 -> roleNames.get(perm.getTargetId());
                case 2 -> userNames.get(perm.getTargetId());
                case 3 -> deptNames.get(perm.getTargetId());
                default -> null;
            });
        }
    }

    private void validatePermItem(CollabRequests.PermItem item) {
        int targetType = item.targetType();
        if (targetType < 0 || targetType > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "targetType 取值不合法（0全员/1角色/2用户/3部门）");
        }
        if (targetType == 0) {
            if (!Objects.equals(item.targetId(), 0L)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "全员授权 targetId 必须为 0");
            }
        } else if (targetType == 2) {
            lookup.requireUser(item.targetId());
        } else if (targetType == 1) {
            if (lookup.roleNames(Set.of(item.targetId())).isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在");
            }
        } else {
            if (lookup.deptNames(Set.of(item.targetId())).isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "部门不存在");
            }
        }
        int permType = item.permType();
        if (permType < PERM_VIEW || permType > PERM_EDIT) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "permType 取值不合法（1查看/2下载/3编辑）");
        }
    }

    private Long requireCategory(Long categoryId) {
        if (categoryId == null || categoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文档分类不存在");
        }
        return categoryId;
    }

    private String requireName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文档名称不能为空");
        }
        String name = value.trim();
        if (name.length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文档名称长度不能超过 200");
        }
        return name;
    }

    /** 次版本 +1：V1.0 → V1.1，V1.9 → V1.10，主版本不动。 */
    static String bumpVersion(String version) {
        String current = StringUtils.hasText(version) ? version.trim() : "V1.0";
        Matcher matcher = VERSION_PATTERN.matcher(current);
        if (matcher.matches()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            return "V" + major + "." + (minor + 1);
        }
        return "V1.1";
    }

    private Map<String, Object> stateOf(CollDoc doc) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", doc.getId());
        result.put("docNo", doc.getDocNo());
        result.put("status", doc.getStatus());
        result.put("version", doc.getVersion());
        return result;
    }
}

