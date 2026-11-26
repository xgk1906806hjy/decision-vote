package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SolutionReq {

    @NotBlank(message = "方案内容不能为空")
    @Size(max = 500, message = "方案最多 500 字")
    private String content;
}
