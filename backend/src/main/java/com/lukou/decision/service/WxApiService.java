package com.lukou.decision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.config.WxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 微信登录：用 wx.login 拿到的 code 换 openid。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxApiService {

    private static final String CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";

    private final WxProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public String code2Openid(String code) {
        if (props.isMock()) {
            // 本地联调：同一个 code 稳定映射到同一个 openid
            return "mock_" + Math.abs(code.hashCode());
        }
        if (props.getAppId() == null || props.getAppId().isBlank()) {
            throw new BusinessException(500, "未配置 WX_APPID");
        }
        String url = CODE2SESSION
                + "?appid=" + props.getAppId()
                + "&secret=" + props.getAppSecret()
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("openid")) {
                return node.get("openid").asText();
            }
            log.warn("code2Session 返回异常: {}", body);
            throw new BusinessException(401, "微信登录失败：" + node.path("errmsg").asText("未知错误"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信登录接口失败", e);
            throw new BusinessException(500, "微信登录服务不可用");
        }
    }
}
