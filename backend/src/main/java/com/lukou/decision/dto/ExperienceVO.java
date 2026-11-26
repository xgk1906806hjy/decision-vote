package com.lukou.decision.dto;

import lombok.Data;

@Data
public class ExperienceVO {

    private Long id;
    private String scene;
    private String choice;
    private Integer yearsAgo;
    private String outcome;
}
