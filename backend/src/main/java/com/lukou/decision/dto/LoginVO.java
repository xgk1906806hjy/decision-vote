package com.lukou.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private Long userId;
    private String nickname;
    private String avatar;
    private boolean newUser;
}
