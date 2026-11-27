package com.lukou.decision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.config.WxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信服务端接口：登录 code2Session、access_token、内容安全 msgSecCheck。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxApiService {

    private static final String CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String ACCESS_TOKEN = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String MSG_SEC_CHECK = "https://api.weixin.qq.com/wxa/msg_sec_check";
    private static final String MEDIA_CHECK_ASYNC = "https://api.weixin.qq.com/wxa/media_check_async";
    private static final String SUBSCRIBE_SEND = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";

    private final WxProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    private volatile String accessToken;
    private volatile long accessTokenExpireAt;

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

    /**
     * 调用微信内容安全 2.0 接口。返回 pass / review / risky。
     * access_token 失效时自动刷新并重试一次。
     */
    public CheckResult msgSecCheck(String content, String openid, int scene) {
        if (props.isMock()) {
            throw new BusinessException(500, "WX_MOCK=true 时不能调用内容安全接口");
        }
        try {
            String token = getAccessToken();
            JsonNode node = callMsgSecCheck(token, content, openid, scene);
            int errcode = node.path("errcode").asInt(0);

            if (errcode == 40001 || errcode == 42001) {
                invalidateAccessToken();
                token = getAccessToken();
                node = callMsgSecCheck(token, content, openid, scene);
                errcode = node.path("errcode").asInt(0);
            }

            if (errcode != 0) {
                log.warn("msgSecCheck 返回异常: {}", node);
                throw new BusinessException(500, "微信内容安全服务返回异常");
            }

            String suggest = node.path("result").path("suggest").asText("pass");
            int label = node.path("result").path("label").asInt(0);
            return new CheckResult(suggest, label);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信内容安全接口失败", e);
            throw new BusinessException(500, "微信内容安全服务不可用");
        }
    }

    /**
     * 图片异步送审（mediaCheckAsync 2.0）。返回 trace_id；
     * 审核结果由微信推送到「消息推送」回调地址（/api/wx/callback），不是同步返回。
     * mediaUrl 必须公网可访问，微信服务器要能拉到图。
     */
    public String mediaCheckAsync(String mediaUrl, String openid, int scene) {
        if (props.isMock()) {
            throw new BusinessException(500, "WX_MOCK=true 时不能调用内容安全接口");
        }
        try {
            String token = getAccessToken();
            JsonNode node = callMediaCheck(token, mediaUrl, openid, scene);
            int errcode = node.path("errcode").asInt(0);
            if (errcode == 40001 || errcode == 42001) {
                invalidateAccessToken();
                node = callMediaCheck(getAccessToken(), mediaUrl, openid, scene);
                errcode = node.path("errcode").asInt(0);
            }
            if (errcode != 0 || !node.hasNonNull("trace_id")) {
                log.warn("mediaCheckAsync 返回异常: {}", node);
                throw new BusinessException(500, "微信图片审核服务返回异常");
            }
            return node.get("trace_id").asText();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信图片审核接口失败", e);
            throw new BusinessException(500, "微信图片审核服务不可用");
        }
    }

    private JsonNode callMediaCheck(String token, String mediaUrl, String openid, int scene) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("media_url", mediaUrl);
        payload.put("media_type", 2); // 2=图片
        payload.put("version", 2);
        payload.put("scene", scene);
        payload.put("openid", openid);

        String body = restClient.post()
                .uri(MEDIA_CHECK_ASYNC + "?access_token={token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body);
    }

    /**
     * 发送一次性订阅消息。返回微信的 errcode，0 表示成功。
     * 40001/42001（token 失效）自动刷新重试一次；其余错误码原样返回，由调用方决定是否消耗额度。
     */
    public SendResult sendSubscribeMessage(String openid, String tmplId, String page,
                                           Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("touser", openid);
        payload.put("template_id", tmplId);
        if (page != null && !page.isBlank()) {
            payload.put("page", page);
        }
        payload.put("data", data);
        payload.put("miniprogram_state", "formal");
        payload.put("lang", "zh_CN");

        try {
            String token = getAccessToken();
            JsonNode node = callSubscribeSend(token, payload);
            int errcode = node.path("errcode").asInt(0);
            if (errcode == 40001 || errcode == 42001) {
                invalidateAccessToken();
                node = callSubscribeSend(getAccessToken(), payload);
                errcode = node.path("errcode").asInt(0);
            }
            if (errcode != 0) {
                log.warn("订阅消息发送失败 openid={} errcode={} errmsg={}",
                        mask(openid), errcode, node.path("errmsg").asText(""));
            }
            return new SendResult(errcode, node.path("errmsg").asText(""));
        } catch (Exception e) {
            log.error("调用订阅消息接口异常 openid={}", mask(openid), e);
            return new SendResult(-1, e.getMessage());
        }
    }

    private JsonNode callSubscribeSend(String token, Map<String, Object> payload) throws Exception {
        String body = restClient.post()
                .uri(SUBSCRIBE_SEND + "?access_token={token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body);
    }

    private static String mask(String openid) {
        if (openid == null || openid.length() <= 8) {
            return openid;
        }
        return openid.substring(0, 4) + "***" + openid.substring(openid.length() - 4);
    }

    private JsonNode callMsgSecCheck(String token, String content, String openid, int scene) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("version", 2);
        payload.put("scene", scene);
        payload.put("openid", openid);

        String body = restClient.post()
                .uri(MSG_SEC_CHECK + "?access_token={token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body);
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < accessTokenExpireAt) {
            return accessToken;
        }
        if (props.getAppId() == null || props.getAppId().isBlank()
                || props.getAppSecret() == null || props.getAppSecret().isBlank()) {
            throw new BusinessException(500, "未配置 WX_APPID / WX_SECRET");
        }

        String url = ACCESS_TOKEN
                + "?grant_type=client_credential"
                + "&appid=" + props.getAppId()
                + "&secret=" + props.getAppSecret();
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(body);
            if (!node.hasNonNull("access_token")) {
                log.warn("获取 access_token 失败: {}", body);
                throw new BusinessException(500, "获取微信 access_token 失败");
            }
            accessToken = node.get("access_token").asText();
            long expiresIn = node.path("expires_in").asLong(7200);
            // 提前 5 分钟过期，避免边界失效
            accessTokenExpireAt = now + Math.max(60, expiresIn - 300) * 1000L;
            return accessToken;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取微信 access_token 异常", e);
            throw new BusinessException(500, "获取微信 access_token 异常");
        }
    }

    private synchronized void invalidateAccessToken() {
        accessToken = null;
        accessTokenExpireAt = 0;
    }

    public record CheckResult(String suggest, int label) {
        public boolean isRisky() {
            return "risky".equalsIgnoreCase(suggest);
        }

        public boolean isReview() {
            return "review".equalsIgnoreCase(suggest);
        }
    }

    public record SendResult(int errcode, String errmsg) {
        public boolean ok() {
            return errcode == 0;
        }

        /** 43101 = 用户拒收/未订阅；47003 = 模板参数错误。这两类重试无意义 */
        public boolean shouldStopReminding() {
            return errcode == 43101 || errcode == 47003;
        }
    }
}
