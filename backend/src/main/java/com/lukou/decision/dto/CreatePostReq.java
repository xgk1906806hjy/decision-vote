package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostReq {

    @NotBlank(message = "标题不能为空")
    @Size(max = 60, message = "标题最多 60 字")
    private String title;

    @NotBlank(message = "请把情况说清楚")
    @Size(max = 2000, message = "描述最多 2000 字")
    private String content;

    private String scene;

    /**
     * 1 = 匿名展示
     */
    private Integer anonymous = 0;

    /**
     * 投票时长（小时），null 表示不设截止
     */
    private Integer deadlineHours;

    @Size(min = 2, max = 6, message = "选项需要 2 到 6 个")
    private List<String> options;
}
