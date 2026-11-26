package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案例库条目：一条已完成回访、带结局的决策记录。
 */
@Data
public class CaseVO {

    private Long postId;
    private String title;
    private String content;
    private String scene;
    private String chosenLabel;
    private String outcome;
    private String outcomeLabel;
    private String outcomeNote;
    private int voteCount;
    private int commentCount;
    private LocalDateTime answeredAt;
    /** 相似度，相似推荐接口返回；普通列表为 0 */
    private int similarity;
}
