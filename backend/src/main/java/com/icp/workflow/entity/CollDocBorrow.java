package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 文档借阅申请（docs/04 §3 coll_doc_borrow，M2c 走 doc_borrow 流程）。
 * status：0 申请中 / 1 已通过 / 2 已驳回 / 3 已归还。
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
    private String borrowerName;
}
