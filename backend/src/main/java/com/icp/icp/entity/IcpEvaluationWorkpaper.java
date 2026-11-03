package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价底稿（docs/04 §3 icp_evaluation_workpaper，本表无 remark 字段）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_evaluation_workpaper")
public class IcpEvaluationWorkpaper extends BaseEntity {

    private Long taskId;
    private String sampleDesc;
    private String testDesc;
    private Integer testResult;
    private String evidence;
    private String conclusion;
    private Long workUserId;
}
