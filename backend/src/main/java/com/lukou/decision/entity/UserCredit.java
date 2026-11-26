package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dv_user_credit")
public class UserCredit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String scene;
    private Integer stars;
}
