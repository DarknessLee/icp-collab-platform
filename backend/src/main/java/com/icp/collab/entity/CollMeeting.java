package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会议纪要 coll_meeting：类型 0周例会 / 1专题会 / 2评审会 / 3培训会；状态 0待召开 / 1已召开 / 2已取消。
 * 纪要正文 minutes 在「录入纪要」时写入并将状态置为已召开。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_meeting")
public class CollMeeting extends BaseEntity {

    private String meetingNo;

    private String title;

    /** 0周例会 / 1专题会 / 2评审会 / 3培训会 */
    private String meetingType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String location;

    private Long organizerId;

    private String organizerName;

    private Long deptId;

    private String deptName;

    /** 参会人姓名，逗号分隔 */
    private String attendeeNames;

    private Integer attendeeCount;

    private String minutes;

    /** 0待召开 / 1已召开 / 2已取消 */
    private Integer status;

    private String remark;
}
