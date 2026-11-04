package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文档分类 coll_doc_category（树形，parent_id=0 为根）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_doc_category")
public class CollDocCategory extends BaseEntity {

    private Long parentId;

    private String categoryName;

    private Integer orderNum;

    /** 1正常 0停用 */
    private Integer status;

    @TableField(exist = false)
    private List<CollDocCategory> children;
}
