package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 风险分类（docs/04 §3 icp_risk_category）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_risk_category")
public class IcpRiskCategory extends BaseEntity {

    private Long parentId;
    private String categoryName;
    private Integer orderNum;
    private Integer status;

    @TableField(exist = false)
    private List<IcpRiskCategory> children = new ArrayList<>();
}
