package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告 coll_notice：状态 0草稿 / 1已发布 / 2已关闭。
 * 范围在 dept_scope 中编码：全公司=scope1；DEPT:2,3=scope2；ROLE:EMPLOYEE,AUDITOR=scope3。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_notice")
public class CollNotice extends BaseEntity {

    private String noticeTitle;

    /** 1通知 2公告 3制度宣贯 */
    private Integer noticeType;

    private String content;

    private Integer status;

    private Long publisherId;

    private LocalDateTime publishTime;

    private Integer isTop;

    private String deptScope;

    private String remark;

    @TableField(exist = false)
    private Integer scope;

    @TableField(exist = false)
    private List<Long> scopeDeptIds;

    @TableField(exist = false)
    private List<String> scopeRoles;

    @TableField(exist = false)
    private String publisherName;

    @TableField(exist = false)
    private Boolean read;
}
