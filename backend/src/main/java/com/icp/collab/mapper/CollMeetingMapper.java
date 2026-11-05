package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollMeeting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollMeetingMapper extends BaseMapper<CollMeeting> {

    /**
     * 会议编号最大值（HY-yyyy-###），用于 CodeSeq 生成下一序号。
     * <p>注意：meeting_no 有唯一约束且逻辑删除行仍占用编号，因此这里不按 deleted 过滤，
     * 否则删除会议后再次新增会与已删除行冲突。</p>
     */
    @Select("SELECT MAX(meeting_no) FROM coll_meeting "
            + "WHERE meeting_no REGEXP '^HY-[0-9]{4}-[0-9]{1,6}$'")
    String selectMaxNo();
}
