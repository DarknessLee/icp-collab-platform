package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程/会议 coll_schedule：类型 1会议 / 2提醒 / 3日程；状态 0待开始 / 1进行中 / 2已结束 / 3已取消。
 * 跨部门会议通过 participants（用户 ID 列表）参与。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_schedule")
public class CollSchedule extends BaseEntity {

    private String scheduleTitle;

    private Integer scheduleType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    /** 参与人用户 ID，CSV */
    private String participants;

    private Integer remindMinutes;

    private Long creatorId;

    private Long deptId;

    private Integer status;

    private String description;

    private String remark;

    @TableField(exist = false)
    private String creatorName;

    @TableField(exist = false)
    private List<Long> participantIds;

    @TableField(exist = false)
    private List<String> participantNames;
}
