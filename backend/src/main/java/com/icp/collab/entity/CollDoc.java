package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文档 coll_doc；权限见 coll_doc_perm（M2d D37）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_doc")
public class CollDoc extends BaseEntity {

    private String docNo;

    private String docName;

    private Long categoryId;

    /** 1制度 2模板 3报告 4会议纪要 5其他 */
    private Integer docType;

    private String version;

    private Long fileId;

    private Long deptId;

    private Long ownerUserId;

    /** 0草稿 1已发布 2已归档 */
    private Integer status;

    private Integer downloadCount;

    private String description;

    private String remark;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String ownerName;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private Boolean canView;

    @TableField(exist = false)
    private Boolean canDownload;

    @TableField(exist = false)
    private List<CollDocPerm> perms;
}
