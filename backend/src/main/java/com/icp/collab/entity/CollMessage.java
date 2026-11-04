package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内消息 coll_message（M2d 消息中心 + 任务提醒/系统消息）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_message")
public class CollMessage extends BaseEntity {

    /** 1系统 2审批 3任务 4公告 5预警 */
    private Integer messageType;

    private String title;

    private String content;

    private String bizType;

    private Long bizId;

    private Long senderId;

    private Long receiverId;

    /** 0未读 1已读 */
    private Integer readFlag;

    private LocalDateTime readTime;

    private String remark;

    @TableField(exist = false)
    private String senderName;
}
