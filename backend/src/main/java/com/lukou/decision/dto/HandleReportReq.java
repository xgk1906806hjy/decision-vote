package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class HandleReportReq {

    /**
     * hide = 隐藏被举报内容；dismiss = 驳回举报。
     */
    @NotBlank(message = "请选择处理动作")
    @Pattern(regexp = "hide|dismiss", message = "处理动作只能是 hide 或 dismiss")
    private String action;
}
