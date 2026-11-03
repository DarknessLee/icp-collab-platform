package com.icp.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户信息：GET /api/v1/auth/me 返回。
 */
@Data
public class MeResponse {

    private UserInfo user;
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<MenuNode> menus = new ArrayList<>();
}
