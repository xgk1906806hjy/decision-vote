package com.lukou.decision.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteReq {

    @NotNull(message = "请选择一个选项")
    private Long optionId;
}
