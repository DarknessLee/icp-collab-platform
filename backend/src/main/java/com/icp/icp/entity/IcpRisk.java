package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 风险（docs/04 §3 icp_risk）：风险值 = 可能性 × 影响，等级 high≥15 / mid 8–14 / low≤7。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_risk")
public class IcpRisk extends BaseEntity {

    private String riskCode;
    private String riskName;
    private Long categoryId;
    private Integer riskLevel;
    private Integer probability;
    private Integer impact;
    private Integer riskScore;
    private Long ownerDeptId;
    private Long ownerUserId;
    private Integer status;
    private String description;
    private String remark;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String ownerDeptName;

    @TableField(exist = false)
    private BigDecimal coverageRate;
}
