package com.lukou.decision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /**
     * 举报后台访问令牌，通过请求头 X-Admin-Token 传入。
     * 生产环境必须通过 ADMIN_TOKEN 覆盖。
     */
    private String token = "change-me-in-production";
}
