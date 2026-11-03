package com.icp.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程表单（docs/04 §3 wf_form）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form")
public class WfForm extends BaseEntity {

    private String formCode;
    private String formName;
    private String formSchema;
    private Integer version;
    private Integer status;
    private String remark;
}
