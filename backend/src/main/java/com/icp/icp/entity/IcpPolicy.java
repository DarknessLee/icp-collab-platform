package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 制度（docs/04 §3 icp_policy）。
 *
 * <p>状态机：0 草稿 → 1 审批中 → 2 已生效 → 3 已废止；2 → 4 已过期（由 expire_date 派生）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_policy")
public class IcpPolicy extends BaseEntity {

    private String policyCode;
    private String policyName;
    private Long categoryId;
    private String version;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer status;
    private String content;
    private Long ownerDeptId;
    private Long ownerUserId;
    private String keywords;
    private String flowInstanceId;
    private LocalDateTime publishTime;
    private String remark;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String ownerDeptName;
}
