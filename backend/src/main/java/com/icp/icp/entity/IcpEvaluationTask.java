package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 评价任务（docs/04 §3 icp_evaluation_task）：0 待开始 → 1 进行中 → 2 已完成；3 逾期（派生状态）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_evaluation_task")
public class IcpEvaluationTask extends BaseEntity {

    private String taskCode;
    private Long planId;
    private String taskName;
    private Long riskId;
    private Long controlId;
    private Long evaluateDeptId;
    private Long evaluatorId;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer status;
    private BigDecimal score;
    private Integer conclusion;
    private String remark;

    @TableField(exist = false)
    private String planName;

    @TableField(exist = false)
    private String riskName;

    @TableField(exist = false)
    private String controlName;

    @TableField(exist = false)
    private Integer workpaperCount;
}
