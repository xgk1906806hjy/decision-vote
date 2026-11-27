package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportVO {

    private Long id;
    private String targetType;
    private Long targetId;
    private Long postId;
    private String reason;
    private Integer handled;
    private String action;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;

    private String reporterName;
    private String targetTitle;
    private String targetExcerpt;
    private String targetStatus;
}
