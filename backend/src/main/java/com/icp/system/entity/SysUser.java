package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private Long deptId;
    private Long postId;
    private String username;
    private String password;
    private String realName;
    private Integer gender;
    private String mobile;
    private String email;
    private String avatar;
    private Integer status;
    private String loginIp;
    private LocalDateTime loginTime;
    private LocalDateTime pwdUpdateTime;
    private Integer failCount;
    private LocalDateTime lockUntil;
    private String remark;
}
