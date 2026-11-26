package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_solution")
public class Solution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private String content;
    private Integer adopted;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createdAt;
}
