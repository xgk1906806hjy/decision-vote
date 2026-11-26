package com.lukou.decision.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {

    @NotBlank(message = "缺少登录 code")
    private String code;

    /**
     * 可选：用户主动授权后带上的昵称和头像
     */
    private String nickname;
    private String avatar;
}
