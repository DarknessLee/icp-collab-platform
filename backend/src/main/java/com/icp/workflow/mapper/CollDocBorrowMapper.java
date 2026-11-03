package com.icp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.workflow.entity.CollDocBorrow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CollDocBorrowMapper extends BaseMapper<CollDocBorrow> {

    @Select("SELECT COUNT(*) FROM coll_doc WHERE id = #{docId} AND deleted = 0")
    long countDoc(@Param("docId") Long docId);
}
