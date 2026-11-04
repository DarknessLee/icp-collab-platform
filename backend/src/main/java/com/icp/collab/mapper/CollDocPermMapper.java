package com.icp.collab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.collab.entity.CollDocPerm;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollDocPermMapper extends BaseMapper<CollDocPerm> {

    /** uk_doc_perm 不含 deleted，覆盖授权时必须物理删除旧行。 */
    @Delete("DELETE FROM coll_doc_perm WHERE doc_id = #{docId}")
    int deleteByDocId(Long docId);
}
