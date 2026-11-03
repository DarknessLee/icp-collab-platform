package com.icp.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 前端动态路由/菜单节点。
 */
@Data
public class MenuNode {

    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private String type;
    private String perms;
    private Integer orderNum;
    private List<MenuNode> children = new ArrayList<>();
}
