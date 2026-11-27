package com.lukou.decision.dto;

import lombok.Data;

@Data
public class SceneStatVO {

    private String scene;
    private int total;
    private int worth;
    private int regret;
    private int watching;
    private int worthRate;
    private int regretRate;
}
