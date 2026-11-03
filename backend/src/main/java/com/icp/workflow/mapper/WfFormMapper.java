package com.icp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.workflow.entity.WfForm;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WfFormMapper extends BaseMapper<WfForm> {

    @Select("SELECT * FROM wf_form WHERE form_code = #{formCode} AND deleted = 0 LIMIT 1")
    WfForm selectByFormCode(@Param("formCode") String formCode);
}
