package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportReq {

    /**
     * 被举报对象类型：post（决策帖）/ comment（建议）
     */
    @NotBlank(message = "缺少举报对象类型")
    private String targetType;

    @NotNull(message = "缺少举报对象编号")
    private Long targetId;

    @NotBlank(message = "请选择举报原因")
    @Size(max = 255, message = "举报原因最多 255 字")
    private String reason;
}
