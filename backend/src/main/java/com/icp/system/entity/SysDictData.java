package com.icp.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.icp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer dictSort;
    private String tagType;
    private Integer status;
    private String remark;
}
