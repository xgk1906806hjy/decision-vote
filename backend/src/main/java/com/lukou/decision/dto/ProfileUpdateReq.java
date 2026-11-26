package com.lukou.decision.dto;

import lombok.Data;

@Data
public class ProfileUpdateReq {
    private String nickname;
    private String avatar;
    private String bio;
}
