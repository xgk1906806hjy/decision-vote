package com.lukou.decision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.content")
public class ContentProperties {

    /**
     * true 时只走本地词库，不调用微信内容安全接口。
     */
    private boolean auditMock = true;

    /**
     * 微信 msgSecCheck 的 scene：1 资料 / 2 评论 / 3 论坛 / 4 社交日志。
     */
    private int scene = 3;

    /**
     * 微信接口不可用时是否放行。默认 false（失败关闭），避免内容安全接口故障时绕过审核。
     */
    private boolean failOpen = false;
}
