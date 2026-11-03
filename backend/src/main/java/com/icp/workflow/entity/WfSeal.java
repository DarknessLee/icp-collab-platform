package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 用印申请（docs/04 §3 wf_seal）。
 * status：0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已完成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_seal")
public class WfSeal extends BaseEntity {

    private String applyNo;
    private Integer sealType;
    private String reason;
    private LocalDate useDate;
    private Long fileId;
    private Long applicantId;
    private Long deptId;
    private Integer status;
    private String flowInstanceId;
    private String remark;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private String applicantName;
}
