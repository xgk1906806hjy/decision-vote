package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private Long optionId;
    private String content;
    private String outcomeTag;
    private Integer status;
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
