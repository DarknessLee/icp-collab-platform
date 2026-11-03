package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购申请（docs/04 §3 wf_purchase）。
 * status：0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_purchase")
public class WfPurchase extends BaseEntity {

    private String applyNo;
    private String title;
    private Long deptId;
    private Long applicantId;
    private BigDecimal amount;
    private BigDecimal budgetAmount;
    private String supplier;
    private Integer purchaseType;
    private String purpose;
    private LocalDate applyDate;
    private Integer status;
    private String flowInstanceId;
    private String remark;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private String applicantName;
}
