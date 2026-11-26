package com.lukou.decision.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FollowUpReq {

    private Long choseOptionId;

    /**
     * worth / regret / watching
     */
    private String outcome;

    @Size(max = 1000, message = "心得最多 1000 字")
    private String note;
}
