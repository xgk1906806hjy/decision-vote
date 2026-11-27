package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    /**
     * 回答者身份：experienced=我经历过 / passerby=我只是路过。
     * 不传时后端按 passerby 处理，兼容旧客户端。
     */
    @Pattern(regexp = "experienced|passerby", message = "身份只能是 experienced 或 passerby")
    private String authorType;
}
