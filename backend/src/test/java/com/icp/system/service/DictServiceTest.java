package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysDictData;
import com.icp.system.entity.SysDictType;
import com.icp.system.mapper.SysDictDataMapper;
import com.icp.system.mapper.SysDictTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 字典单测：类型唯一、数据项同键入唯一、局部更新时先合并再校验。
 */
@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private SysDictTypeMapper typeMapper;
    @Mock
    private SysDictDataMapper dataMapper;

    private DictService dictService;

    @BeforeEach
    void setUp() {
        dictService = new DictService(typeMapper, dataMapper);
    }

    @Test
    void duplicateTypeRejected() {
        when(typeMapper.selectOne(any())).thenReturn(new SysDictType());
        SysDictType request = new SysDictType();
        request.setDictName("风险等级");
        request.setDictType("risk_level");

        assertThatThrownBy(() -> dictService.createType(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void duplicateItemValueRejected() {
        when(dataMapper.selectOne(any())).thenReturn(new SysDictData());
        SysDictData request = new SysDictData();
        request.setDictType("risk_level");
        request.setDictLabel("高");
        request.setDictValue("3");

        assertThatThrownBy(() -> dictService.createItem(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void partialItemUpdateMergesThenValidates() {
        SysDictData existing = new SysDictData();
        existing.setId(1L);
        existing.setDictType("risk_level");
        existing.setDictLabel("高");
        existing.setDictValue("3");
        existing.setStatus(1);
        when(dataMapper.selectById(1L)).thenReturn(existing);

        SysDictData request = new SysDictData();
        request.setStatus(0);
        SysDictData updated = dictService.updateItem(1L, request);

        assertThat(updated.getStatus()).isZero();
        assertThat(updated.getDictValue()).isEqualTo("3");
    }

    @Test
    void itemsAreFilteredByType() {
        SysDictData item = new SysDictData();
        item.setDictType("risk_level");
        when(dataMapper.selectList(any())).thenReturn(List.of(item));

        assertThat(dictService.items("risk_level", 1)).hasSize(1);
    }

    @Test
    void deleteBuiltinTypeRejected() {
        SysDictType existing = new SysDictType();
        existing.setId(2L);
        existing.setDictType("icp_risk_level");
        when(typeMapper.selectById(2L)).thenReturn(existing);

        assertThatThrownBy(() -> dictService.deleteType(2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void deleteTypeWithItemsRejected() {
        SysDictType existing = new SysDictType();
        existing.setId(21L);
        existing.setDictType("qa_type_1");
        when(typeMapper.selectById(21L)).thenReturn(existing);
        when(dataMapper.selectCount(any())).thenReturn(3L);

        assertThatThrownBy(() -> dictService.deleteType(21L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void deleteTypeWithoutItemsRemovesType() {
        SysDictType existing = new SysDictType();
        existing.setId(21L);
        existing.setDictType("qa_type_1");
        when(typeMapper.selectById(21L)).thenReturn(existing);
        when(dataMapper.selectCount(any())).thenReturn(0L);

        dictService.deleteType(21L);

        org.mockito.Mockito.verify(typeMapper).deleteById(21L);
    }

    @Test
    void deleteMissingTypeReturnsNotFound() {
        when(typeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> dictService.deleteType(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }
}
