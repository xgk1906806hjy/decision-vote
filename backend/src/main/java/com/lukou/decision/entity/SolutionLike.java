package com.lukou.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dv_solution_like")
public class SolutionLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long solutionId;
    private Long userId;
    private LocalDateTime createdAt;
}
