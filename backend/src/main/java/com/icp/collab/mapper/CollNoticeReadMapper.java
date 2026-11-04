package com.icp.collab.mapper;

import com.icp.collab.entity.CollNoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CollNoticeReadMapper {

    @Insert("INSERT IGNORE INTO coll_notice_read(notice_id, user_id, read_time) "
            + "VALUES(#{noticeId}, #{userId}, #{readTime})")
    int insertIgnore(CollNoticeRead record);

    @Select("SELECT COUNT(*) FROM coll_notice_read WHERE notice_id = #{noticeId} AND user_id = #{userId}")
    long countRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    @Select("SELECT notice_id FROM coll_notice_read WHERE user_id = #{userId}")
    List<Long> selectReadNoticeIds(Long userId);

    @Select("SELECT COUNT(*) FROM coll_notice_read WHERE notice_id = #{noticeId}")
    long countReaders(Long noticeId);
}
