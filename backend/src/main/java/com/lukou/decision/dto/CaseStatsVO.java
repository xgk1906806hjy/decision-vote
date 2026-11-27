package com.lukou.decision.dto;

import lombok.Data;

import java.util.List;

@Data
public class CaseStatsVO {

    private int total;
    private int worth;
    private int regret;
    private int watching;
    private int unknown;
    private int worthRate;
    private int regretRate;
    private int watchingRate;
    private List<SceneStatVO> scenes;
}
