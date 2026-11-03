package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 控制点（docs/04 §3 icp_control）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_control")
public class IcpControl extends BaseEntity {

    private String controlCode;
    private String controlName;
    private Integer controlType;
    private String controlDesc;
    private String freq;
    private Long ownerDeptId;
    private Long ownerUserId;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private String ownerDeptName;
}
