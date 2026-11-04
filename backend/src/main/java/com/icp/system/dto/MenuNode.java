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
    /** 是否显示（1 显示 / 0 隐藏）；隐藏节点不会出现在菜单树中，前端可据此兜底。 */
    private Integer visible;
    private List<MenuNode> children = new ArrayList<>();
}
