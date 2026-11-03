package com.icp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.workflow.entity.WfProcessMeta;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WfProcessMetaMapper extends BaseMapper<WfProcessMeta> {

    @Select("SELECT * FROM wf_process_meta WHERE process_key = #{processKey} AND deleted = 0 LIMIT 1")
    WfProcessMeta selectByProcessKey(@Param("processKey") String processKey);
}
