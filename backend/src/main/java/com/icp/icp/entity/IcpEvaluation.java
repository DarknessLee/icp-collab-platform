package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 内控评价计划（docs/04 §3 icp_evaluation）：0 草稿 → 1 审批中 → 2 进行中 → 3 已完成 → 4 已关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_evaluation")
public class IcpEvaluation extends BaseEntity {

    private String planCode;
    private String planName;
    private Integer planYear;
    private Integer planType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String deptScope;
    private Integer status;
    private Long ownerDeptId;
    private Long ownerUserId;
    private String flowInstanceId;
    private String description;
    private String remark;

    @TableField(exist = false)
    private String ownerDeptName;

    @TableField(exist = false)
    private Integer taskCount;
}
