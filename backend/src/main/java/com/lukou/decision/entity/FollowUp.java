package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_follow_up")
public class FollowUp {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private Long choseOptionId;
    private String outcome;
    private String note;
    private LocalDateTime dueAt;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;
}
