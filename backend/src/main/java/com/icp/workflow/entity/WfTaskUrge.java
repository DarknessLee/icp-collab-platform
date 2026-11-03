package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 流程催办记录（docs/04 §3 wf_task_urge）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task_urge")
public class WfTaskUrge extends BaseEntity {

    private String processInstanceId;
    private String taskId;
    private String businessNo;
    private Long urgeUserId;
    private Long targetUserId;
    private String content;
    private LocalDateTime urgeTime;
}
