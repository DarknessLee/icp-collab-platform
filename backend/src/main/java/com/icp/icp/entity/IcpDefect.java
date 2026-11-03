package com.icp.icp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缺陷（docs/04 §3 icp_defect）：0 已登记 → 1 整改中 → 2 待复核 → 3 已关闭。
 *
 * <p>注意：本表没有 rectify_id 列，缺陷与整改通过 icp_rectify.defect_id 反向关联（与任务描述里的“回写 rectify_id”有差异，已上报 Lead）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("icp_defect")
public class IcpDefect extends BaseEntity {

    private String defectCode;
    private String defectName;
    private Integer sourceType;
    private Long sourceId;
    private Integer severity;
    private Integer defectType;
    private Long deptId;
    private LocalDate findDate;
    private Long discoverUserId;
    private String description;
    private Integer status;
    private Long ownerUserId;
    private LocalDateTime closeTime;
    private String remark;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private Long rectifyId;
}
