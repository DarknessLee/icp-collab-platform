package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程元数据（docs/04 §3 wf_process_meta）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_meta")
public class WfProcessMeta extends BaseEntity {

    private String processKey;
    private String processName;
    private String category;
    private Integer version;
    private String description;
    private Integer status;
}
