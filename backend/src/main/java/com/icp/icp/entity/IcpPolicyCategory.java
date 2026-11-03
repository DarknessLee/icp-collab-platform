package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 制度分类（docs/04 §3 icp_policy_category）：支持两级或更深树。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_policy_category")
public class IcpPolicyCategory extends BaseEntity {

    private Long parentId;
    private String categoryName;
    private Integer orderNum;
    private Integer status;

    @TableField(exist = false)
    private List<IcpPolicyCategory> children = new ArrayList<>();
}
