package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

import lombok.Data;

/**
 * 文档授权 coll_doc_perm：
 * target_type 1角色 / 2用户 / 3部门（全员约定 target_type=0 + target_id=0），
 * perm_type 1查看 / 2下载 / 3编辑。
 */
@Data
@TableName("coll_doc_perm")
public class CollDocPerm implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    private Integer targetType;

    private Long targetId;

    private Integer permType;

    @TableField(exist = false)
    private String targetName;
}
