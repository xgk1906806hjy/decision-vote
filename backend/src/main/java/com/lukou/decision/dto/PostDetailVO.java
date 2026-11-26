package com.lukou.decision.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostDetailVO extends PostCardVO {

    private String content;
    private boolean author;
    private List<CommentVO> comments;
    /**
     * 帖主最终选择（回访结果），没有填过则为 null
     */
    private Long chosenOptionId;
    private String outcome;
    private String outcomeNote;
    private String outcomeSlot;
}
