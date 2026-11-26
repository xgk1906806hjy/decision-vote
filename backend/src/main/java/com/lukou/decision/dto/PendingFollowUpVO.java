package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PendingFollowUpVO {
    private Long postId;
    private String title;
    private LocalDateTime dueAt;
}
