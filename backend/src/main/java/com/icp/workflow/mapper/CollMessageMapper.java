package com.icp.workflow.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * coll_message 轻量写入（M2c 催办/抄送只落消息，页面在 M2d 实现）。
 */
public interface CollMessageMapper {

    @Insert("INSERT INTO coll_message (message_type, title, content, biz_type, biz_id, sender_id, receiver_id, "
            + "read_flag, tenant_id, create_by, create_time) "
            + "VALUES (#{messageType}, #{title}, #{content}, #{bizType}, #{bizId}, #{senderId}, #{receiverId}, "
            + "0, 0, #{senderId}, NOW())")
    int insertMessage(@Param("messageType") Integer messageType,
                      @Param("title") String title,
                      @Param("content") String content,
                      @Param("bizType") String bizType,
                      @Param("bizId") Long bizId,
                      @Param("senderId") Long senderId,
                      @Param("receiverId") Long receiverId);
}
