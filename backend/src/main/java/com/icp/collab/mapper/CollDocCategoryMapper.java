package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollDocCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CollDocCategoryMapper extends BaseMapper<CollDocCategory> {

    @Select("SELECT COUNT(*) FROM coll_doc_category WHERE parent_id = #{parentId} AND deleted = 0")
    long countChildren(Long parentId);

    @Select("SELECT COUNT(*) FROM coll_doc WHERE category_id = #{categoryId} AND deleted = 0")
    long countDocs(Long categoryId);
}
