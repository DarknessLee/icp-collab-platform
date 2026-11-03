package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 整改（docs/04 §3 icp_rectify）：0 待整改 → 1 待复核 → 2 已通过 → 3 已驳回 → 4 已关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_rectify")
public class IcpRectify extends BaseEntity {

    private String rectifyCode;
    private Long defectId;
    private String rectifyPlan;
    private Long responsibleUserId;
    private Long deptId;
    private LocalDate deadline;
    private LocalDate finishDate;
    private String evidence;
    private Integer status;
    private Long reviewUserId;
    private String reviewOpinion;
    private LocalDateTime reviewTime;
    private String flowInstanceId;
    private String remark;

    @TableField(exist = false)
    private String defectCode;

    @TableField(exist = false)
    private String defectName;

    @TableField(exist = false)
    private String deptName;
}
