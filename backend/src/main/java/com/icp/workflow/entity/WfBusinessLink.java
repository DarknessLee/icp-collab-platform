package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程与业务单据关联（docs/04 §3 wf_business_link）。
 * status：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤回。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_business_link")
public class WfBusinessLink extends BaseEntity {

    private String processInstanceId;
    private String processKey;
    private String businessType;
    private Long businessId;
    private String businessNo;
    private String title;
    private String formCode;
    private Long initiatorId;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;

    @TableField(exist = false)
    private String initiatorName;
}
