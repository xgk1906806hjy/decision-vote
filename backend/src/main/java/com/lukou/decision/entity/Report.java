package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String targetType;
    private Long targetId;
    private Long userId;
    private String reason;
    private Integer handled;
    private String action;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
