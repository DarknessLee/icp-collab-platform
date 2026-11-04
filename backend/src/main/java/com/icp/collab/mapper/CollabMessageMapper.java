package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollabMessageMapper extends BaseMapper<CollMessage> {
}
