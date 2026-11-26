package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;
    private String nickname;
    private String avatar;
    private String bio;

    @TableField(fill = FieldFill.DEFAULT)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
