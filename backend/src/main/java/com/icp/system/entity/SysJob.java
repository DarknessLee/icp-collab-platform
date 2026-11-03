package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    private String jobName;
    private String jobGroup;
    private String cronExpression;
    private String invokeTarget;
    private Integer status;
    private String remark;
}
