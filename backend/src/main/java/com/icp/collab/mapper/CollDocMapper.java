package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollDocMapper extends BaseMapper<CollDoc> {

    @Select("SELECT MAX(doc_no) FROM coll_doc WHERE doc_no REGEXP '^WD-[0-9]{4}-[0-9]{1,6}$'")
    String selectMaxNo();
}
