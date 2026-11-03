package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private Integer roleSort;
    private Integer dataScope;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private List<Long> menuIds = new ArrayList<>();

    @TableField(exist = false)
    private Long userCount;
}
