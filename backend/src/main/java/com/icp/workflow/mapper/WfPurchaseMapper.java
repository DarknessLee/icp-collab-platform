package com.icp.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.workflow.entity.WfPurchase;
import org.apache.ibatis.annotations.Select;

public interface WfPurchaseMapper extends BaseMapper<WfPurchase> {

    @Select("SELECT MAX(apply_no) FROM wf_purchase WHERE apply_no REGEXP '^CG-[0-9]{4}-[0-9]{1,6}$'")
    String selectMaxCode();
}
