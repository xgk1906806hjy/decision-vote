package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dv_option")
public class Option {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private String label;
    private Integer sortNo;
}
