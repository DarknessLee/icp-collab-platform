package com.icp.collab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 文档借阅 coll_doc_borrow：状态 0申请中 / 1已通过 / 2已驳回 / 3已归还。
 * 创建即发起 doc_borrow 流程（M2c 流程回调推进状态）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coll_doc_borrow")
public class CollDocBorrow extends BaseEntity {

    private Long docId;

    private Long borrowerId;

    private String reason;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer status;

    private String flowInstanceId;

    private String remark;

    @TableField(exist = false)
    private String docName;

    @TableField(exist = false)
    private String docNo;

    @TableField(exist = false)
    private String borrowerName;

    @TableField(exist = false)
    private String currentNode;

    @TableField(exist = false)
    private Boolean overdue;
}
