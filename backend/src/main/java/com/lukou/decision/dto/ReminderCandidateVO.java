package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待发送的回访提醒（管理端预览用，不落库）。
 */
@Data
public class ReminderCandidateVO {

    private Long postId;
    private Long userId;
    private String title;
    private LocalDateTime dueAt;
    /** 该用户剩余可发送次数，0 表示会被跳过 */
    private int remain;
}
