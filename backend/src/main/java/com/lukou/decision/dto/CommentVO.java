package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {

    private Long id;
    private String userName;
    private String avatar;
    private String content;
    private String outcomeTag;
    private String authorType;
    private String optionLabel;
    private LocalDateTime createdAt;
}
