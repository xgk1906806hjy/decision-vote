package com.lukou.decision.dto;

import lombok.Data;

@Data
public class OptionVO {

    private Long id;
    private String label;
    private int count;
    private int percent;
}
