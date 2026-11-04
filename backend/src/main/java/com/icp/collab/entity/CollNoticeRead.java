package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告已读 coll_notice_read（联合主键，无逻辑删除）。
 */
@Data
@TableName("coll_notice_read")
public class CollNoticeRead {

    private Long noticeId;

    private Long userId;

    private LocalDateTime readTime;
}
