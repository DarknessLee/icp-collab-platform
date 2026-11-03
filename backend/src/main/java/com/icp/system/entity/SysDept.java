package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private Long parentId;
    private String ancestors;
    private String deptName;
    private String deptCode;
    private Integer orderNum;
    private Long leaderUserId;
    private String phone;
    private String email;
    private Integer status;
    private String remark;
}
