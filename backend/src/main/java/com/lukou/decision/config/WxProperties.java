package com.lukou.decision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.wx")
public class WxProperties {

    private String appId;
    private String appSecret;
    /**
     * true 时不调用微信接口，直接用 code 生成测试 openid，方便本地开发。
     */
    private boolean mock = true;

    /**
     * 消息推送 Token：mediaCheckAsync 审核结果回调（/api/wx/callback）的签名校验。
     * 与公众平台「开发管理 → 消息推送」里填的 Token 一致；留空 = 回调接口拒绝服务。
     */
    private String mediaToken;

    /**
     * 消息推送 EncodingAESKey（43 位）：安全模式下解密回调密文。
     * http 回调地址微信强制安全模式，必须配置。
     */
    private String mediaAesKey;
}
