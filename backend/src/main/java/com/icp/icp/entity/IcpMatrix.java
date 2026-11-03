package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 风险控制矩阵（docs/04 §3 icp_matrix）：同一 (risk_id, control_id) 唯一，coverage 1/2/3。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_matrix")
public class IcpMatrix extends BaseEntity {

    private String matrixCode;
    private Long riskId;
    private Long controlId;
    private Long policyId;
    private String processKey;
    private Long postId;
    private Integer coverage;
    private String evidenceDesc;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private String riskName;

    @TableField(exist = false)
    private String controlName;
}
