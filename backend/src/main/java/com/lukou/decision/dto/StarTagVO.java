package com.lukou.decision.dto;

import lombok.Data;

/**
 * 系统根据采纳记录自动生成的领域标签，用户不能自己贴。
 */
@Data
public class StarTagVO {

    private String scene;
    private int stars;
}
