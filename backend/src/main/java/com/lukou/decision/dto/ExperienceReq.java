package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExperienceReq {

    @NotBlank(message = "请填写场景")
    private String scene;

    @NotBlank(message = "请填写当时的选择")
    private String choice;

    private Integer yearsAgo = 0;

    /**
     * worth / regret / watching
     */
    private String outcome;
}
