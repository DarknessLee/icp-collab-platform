package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private Long deptId;
    private Long postId;
    private String username;

    /** 只写：请求可传，响应绝不返回密码散列。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private String postName;

    @TableField(exist = false)
    private List<Long> roleIds = new ArrayList<>();

    @TableField(exist = false)
    private List<String> roleNames = new ArrayList<>();
}
