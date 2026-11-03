package com.icp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.workflow.entity.WfSeal;
import org.apache.ibatis.annotations.Select;

public interface WfSealMapper extends BaseMapper<WfSeal> {

    @Select("SELECT MAX(apply_no) FROM wf_seal WHERE apply_no REGEXP '^YY-[0-9]{4}-[0-9]{1,6}$'")
    String selectMaxCode();
}
