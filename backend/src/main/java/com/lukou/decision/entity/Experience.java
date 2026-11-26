package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_experience")
public class Experience {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String scene;
    private String choice;
    private Integer yearsAgo;
    private String outcome;
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
