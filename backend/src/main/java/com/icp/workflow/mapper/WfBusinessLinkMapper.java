package com.icp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.workflow.entity.WfBusinessLink;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WfBusinessLinkMapper extends BaseMapper<WfBusinessLink> {

    @Select("SELECT * FROM wf_business_link WHERE process_instance_id = #{processInstanceId} AND deleted = 0 LIMIT 1")
    WfBusinessLink selectByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    @Select("SELECT * FROM wf_business_link WHERE business_no = #{businessNo} AND deleted = 0 LIMIT 1")
    WfBusinessLink selectByBusinessNo(@Param("businessNo") String businessNo);
}
