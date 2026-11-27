package com.lukou.decision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 回访订阅消息配置。模板 ID 留空时任务只统计不发送（降级模式）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.subscribe")
public class SubscribeProperties {

    /** 订阅消息模板 ID，留空 = 未接入，任务跳过发送 */
    private String followUpTemplateId;

    /** 点击提醒消息后跳转的小程序页面，{postId} 会被替换 */
    private String followUpPage = "/pages/detail/detail?id={postId}";

    /** 只提醒最近 N 天内到期的决策，避免翻旧账 */
    private int maxAgeDays = 7;

    /** 单次任务最多处理多少条，防止一次打爆微信接口频率 */
    private int batchSize = 50;

    /** 单个用户可囤积的最大提醒次数，防止刷额度 */
    private int maxRemain = 10;

    /** true 时只打印将要发送的内容，不真正调用微信接口 */
    private boolean dryRun = false;
}
