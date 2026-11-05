package com.icp.collab.service;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollDoc;
import com.icp.collab.entity.CollDocPerm;
import com.icp.collab.mapper.CollBorrowMapper;
import com.icp.collab.mapper.CollDocCategoryMapper;
import com.icp.collab.mapper.CollDocMapper;
import com.icp.collab.mapper.CollDocPermMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.service.FileService;
import com.icp.testsupport.MybatisTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M2d 文档授权 / 版本号 / 状态机单测。 */
@ExtendWith(MockitoExtension.class)
class DocServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        MybatisTestSupport.init();
    }

    @Mock
    private CollDocMapper docMapper;

    @Mock
    private CollDocPermMapper permMapper;

    @Mock
    private CollBorrowMapper borrowMapper;

    @Mock
    private CollDocCategoryMapper categoryMapper;

    @Mock
    private CollabLookupService lookup;

    @Mock
    private FileService fileService;

    private DocService service;

    @BeforeEach
    void setUp() {
        service = new DocService(docMapper, permMapper, borrowMapper, categoryMapper, lookup, fileService);
        lenient().when(lookup.currentUserId()).thenReturn(11L);
        lenient().when(lookup.currentDeptId()).thenReturn(2L);
        lenient().when(lookup.currentRoleIds()).thenReturn(List.of(4L));
        lenient().when(lookup.isAdmin()).thenReturn(false);
        lenient().when(permMapper.selectList(any())).thenReturn(List.of());
        lenient().when(borrowMapper.selectList(any())).thenReturn(List.of());
        lenient().when(lookup.roleNames(any())).thenReturn(Map.of());
        lenient().when(lookup.userNames(any())).thenReturn(Map.of());
        lenient().when(lookup.deptNames(any())).thenReturn(Map.of());
    }

    private CollDoc doc(Long ownerId, int status) {
        CollDoc doc = new CollDoc();
        doc.setId(3341L);
        doc.setDocNo("WD-2026-0041");
        doc.setDocName("测试文档");
        doc.setOwnerUserId(ownerId);
        doc.setDeptId(2L);
        doc.setStatus(status);
        doc.setVersion("V1.0");
        doc.setFileId(4001L);
        doc.setDownloadCount(0);
        return doc;
    }

    @Test
    void bumpVersionIncrementsMinor() {
        assertThat(DocService.bumpVersion("V1.0")).isEqualTo("V1.1");
        assertThat(DocService.bumpVersion("V1.9")).isEqualTo("V1.10");
        assertThat(DocService.bumpVersion("V2.3")).isEqualTo("V2.4");
    }

    @Test
    void bumpVersionFallsBackToOneDotOne() {
        assertThat(DocService.bumpVersion(null)).isEqualTo("V1.1");
        assertThat(DocService.bumpVersion("abc")).isEqualTo("V1.1");
    }

    @Test
    void replacePermsPhysicallyOverwrites() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 0));
        when(permMapper.insert(any(CollDocPerm.class))).thenReturn(1);

        List<CollDocPerm> rows = service.replacePerms(3341L, List.of(
                new CollabRequests.PermItem(0, 0L, 1),
                new CollabRequests.PermItem(2, 5L, 2),
                new CollabRequests.PermItem(2, 5L, 2)));

        assertThat(rows).hasSize(2);
        verify(permMapper).deleteByDocId(3341L);
        verify(permMapper, times(2)).insert(any(CollDocPerm.class));
    }

    @Test
    void replacePermsRejectsInvalidTargetType() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 0));

        assertThatThrownBy(() -> service.replacePerms(3341L,
                List.of(new CollabRequests.PermItem(9, 1L, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
        verify(permMapper, never()).deleteByDocId(3341L);
    }

    @Test
    void replacePermsRejectsAllUsersWithNonZeroTarget() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 0));

        assertThatThrownBy(() -> service.replacePerms(3341L,
                List.of(new CollabRequests.PermItem(0, 5L, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void replacePermsRejectsNonOwner() {
        when(docMapper.selectById(3341L)).thenReturn(doc(3L, 0));

        assertThatThrownBy(() -> service.replacePerms(3341L,
                List.of(new CollabRequests.PermItem(0, 0L, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void publishMovesDraftToPublished() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 0));

        Map<String, Object> result = service.publish(3341L);

        assertThat(result.get("status")).isEqualTo(1);
        verify(docMapper).updateById(any(CollDoc.class));
    }

    @Test
    void publishRejectsNonDraft() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 1));

        assertThatThrownBy(() -> service.publish(3341L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void archiveRejectsDraft() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 0));

        assertThatThrownBy(() -> service.archive(3341L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void downloadRejectsDraftForNonOwner() {
        when(docMapper.selectById(3341L)).thenReturn(doc(3L, 0));

        assertThatThrownBy(() -> service.download(3341L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(fileService, never()).loadBytes(any());
    }

    @Test
    void downloadRejectsPublishedDocWithoutPermission() {
        when(docMapper.selectById(3341L)).thenReturn(doc(3L, 1));

        assertThatThrownBy(() -> service.download(3341L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void deleteRejectsNonOwnerNonDraft() {
        when(docMapper.selectById(3341L)).thenReturn(doc(3L, 1));

        assertThatThrownBy(() -> service.delete(3341L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(docMapper, never()).deleteById(3341L);
    }

    @Test
    void deleteDraftByOwnerIsLogicalDelete() {
        when(docMapper.selectById(3341L)).thenReturn(doc(11L, 0));

        service.delete(3341L);

        verify(docMapper).deleteById(3341L);
    }
}
