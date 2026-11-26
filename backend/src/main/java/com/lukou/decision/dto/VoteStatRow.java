package com.lukou.decision.dto;

import lombok.Data;

@Data
public class VoteStatRow {
    private Long postId;
    private Long optionId;
    private Integer cnt;
}
