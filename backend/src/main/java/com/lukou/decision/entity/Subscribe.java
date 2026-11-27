package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次性订阅消息额度。用户每点一次「开启提醒」并授权成功，remain + 1；
 * 后端每成功发一条，remain - 1。remain 归零后不再发送，等用户下次授权。
 */
@Data
@TableName("dv_subscribe")
public class Subscribe {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String tmplId;
    private Integer remain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
