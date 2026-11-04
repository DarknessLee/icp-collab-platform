package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 协同任务 coll_task（docs/04 §3.4）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_task")
public class CollTask extends BaseEntity {

    private String taskNo;

    private Long parentId;

    private String taskTitle;

    private Integer taskType;

    private Integer priority;

    private Integer status;

    private Long creatorId;

    private Long assigneeId;

    private Long deptId;

    private LocalDate planStart;

    private LocalDate planEnd;

    private LocalDateTime actualStart;

    private LocalDateTime actualEnd;

    private Integer progress;

    private LocalDateTime remindTime;

    private String description;

    private String remark;

    @TableField(exist = false)
    private String assigneeName;

    @TableField(exist = false)
    private String creatorName;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private String parentTaskTitle;

    @TableField(exist = false)
    private Boolean overdue;

    @TableField(exist = false)
    private List<CollTask> children;

    @TableField(exist = false)
    private List<CollTaskComment> comments;
}
