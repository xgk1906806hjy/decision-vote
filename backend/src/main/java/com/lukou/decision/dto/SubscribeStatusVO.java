package com.lukou.decision.dto;

import lombok.Data;

/**
 * 回访提醒订阅状态。remain 为剩余可发送次数（= 用户授权且未消耗的次数）。
 */
@Data
public class SubscribeStatusVO {

    private int remain;
    /** 单个用户最多可囤积的次数 */
    private int maxRemain;
    /** 后端是否已配置模板 ID，未配置时前端提示"暂未开放" */
    private boolean templateReady;
}
