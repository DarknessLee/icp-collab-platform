package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollDocBorrow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 协同文档借阅申请 Mapper。
 *
 * <p>注意：M2c 流程域已有 {@code com.icp.workflow.mapper.CollDocBorrowMapper}，
 * MyBatis 按类名首字母小写注册 Bean，同名会冲突，故协同域命名为 CollBorrowMapper。</p>
 */
@Mapper
public interface CollBorrowMapper extends BaseMapper<CollDocBorrow> {
}
