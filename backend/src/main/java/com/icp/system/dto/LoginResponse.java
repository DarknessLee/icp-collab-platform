package com.icp.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserInfo user;
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<MenuNode> menus = new ArrayList<>();
}
