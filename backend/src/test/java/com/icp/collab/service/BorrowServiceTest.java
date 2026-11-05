package com.icp.collab.service;

import com.icp.collab.entity.CollDocBorrow;
import com.icp.collab.mapper.CollBorrowMapper;
import com.icp.collab.mapper.CollDocMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.system.service.DataScopeService;
import com.icp.testsupport.MybatisTestSupport;
import com.icp.workflow.service.WorkflowInstanceService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M2d 借阅状态流转 / 归还权限单测。 */
@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        MybatisTestSupport.init();
    }

    @Mock
    private CollBorrowMapper borrowMapper;

    @Mock
    private CollDocMapper docMapper;

    @Mock
    private CollabLookupService lookup;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private WorkflowInstanceService workflowInstanceService;

    private BorrowService service;

    @BeforeEach
    void setUp() {
        service = new BorrowService(borrowMapper, docMapper, lookup, dataScopeService, workflowInstanceService);
        lenient().when(lookup.currentUserId()).thenReturn(11L);
        lenient().when(lookup.currentDeptId()).thenReturn(2L);
        lenient().when(lookup.isAdmin()).thenReturn(false);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(11L, 2L, DataScopeInfo.SELF, List.of()));
    }

    private CollDocBorrow borrow(int status, Long borrowerId) {
        CollDocBorrow borrow = new CollDocBorrow();
        borrow.setId(8001L);
        borrow.setDocId(3341L);
        borrow.setBorrowerId(borrowerId);
        borrow.setStatus(status);
        borrow.setReason("学习制度文件");
        borrow.setStartDate(LocalDate.now());
        borrow.setEndDate(LocalDate.now().plusDays(7));
        return borrow;
    }

    @Test
    void returnBookMovesApprovedToReturned() {
        when(borrowMapper.selectById(8001L)).thenReturn(borrow(1, 11L));

        CollDocBorrow returned = service.returnBook(8001L);

        assertThat(returned.getStatus()).isEqualTo(3);
        verify(borrowMapper).updateById(any(CollDocBorrow.class));
    }

    @Test
    void returnBookRejectsPendingBorrow() {
        when(borrowMapper.selectById(8001L)).thenReturn(borrow(0, 11L));

        assertThatThrownBy(() -> service.returnBook(8001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void returnBookRejectsOtherBorrower() {
        when(borrowMapper.selectById(8001L)).thenReturn(borrow(1, 3L));

        assertThatThrownBy(() -> service.returnBook(8001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void returnBookRejectsOutOfScopeBorrow() {
        when(borrowMapper.selectById(8001L)).thenReturn(borrow(1, 3L));

        assertThatThrownBy(() -> service.returnBook(8001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void createRejectsMissingDocument() {
        when(docMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(new com.icp.collab.dto.CollabRequests.BorrowCreate(
                9999L, "借阅", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createRejectsDraftDocument() {
        com.icp.collab.entity.CollDoc doc = new com.icp.collab.entity.CollDoc();
        doc.setId(3341L);
        doc.setDocName("草稿文档");
        doc.setStatus(0);
        when(docMapper.selectById(3341L)).thenReturn(doc);

        assertThatThrownBy(() -> service.create(new com.icp.collab.dto.CollabRequests.BorrowCreate(
                3341L, "借阅", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }
}
