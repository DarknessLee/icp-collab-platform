package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollTaskMapper extends BaseMapper<CollTask> {

    /** 当前最大任务编号（含逻辑删除行，避免 uk_task_no 冲突）。 */
    @Select("SELECT MAX(task_no) FROM coll_task WHERE task_no REGEXP '^RW-[0-9]{4}-[0-9]{1,6}$'")
    String selectMaxNo();

    @Select("SELECT COUNT(*) FROM coll_task WHERE parent_id = #{parentId} AND deleted = 0")
    long countChildren(Long parentId);
}
