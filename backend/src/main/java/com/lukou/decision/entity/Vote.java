package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_vote")
public class Vote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long optionId;
    private Long userId;
    private LocalDateTime createdAt;
}
