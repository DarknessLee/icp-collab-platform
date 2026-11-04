package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务评论 coll_task_comment。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_task_comment")
public class CollTaskComment extends BaseEntity {

    private Long taskId;

    private Long userId;

    private String content;

    @TableField(exist = false)
    private String userName;
}
