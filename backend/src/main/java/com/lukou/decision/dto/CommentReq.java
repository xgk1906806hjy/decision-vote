package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentReq {

    private Long optionId;

    @NotBlank(message = "写点什么再发吧")
    @Size(max = 1000, message = "评论最多 1000 字")
    private String content;

    /**
     * 给建议的人自己的结果：worth / regret / watching
     */
    private String outcomeTag;
}
