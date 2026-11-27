package com.lukou.decision.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.config.WxProperties;
import com.lukou.decision.entity.MediaCheck;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.MediaCheckMapper;
import com.lukou.decision.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 微信「消息推送」回调：GET 做服务器地址校验，POST 收 mediaCheckAsync 审核结果。
 * 配置位置：公众平台 → 开发管理 → 开发设置 → 消息推送（明文模式，数据格式 JSON/XML 均可）。
 * Token 即环境变量 WX_MEDIA_TOKEN；未配置时本接口拒绝服务。
 */
@Slf4j
@RestController
@RequestMapping("/api/wx")
@RequiredArgsConstructor
public class WxCallbackController {

    private final WxProperties wxProps;
    private final MediaCheckMapper mediaCheckMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 微信服务器地址有效性验证：原样返回 echostr */
    @GetMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam String signature, @RequestParam String timestamp,
                         @RequestParam String nonce, @RequestParam String echostr) {
        requireValidSignature(signature, timestamp, nonce);
        return echostr;
    }

    /**
     * 事件推送。无论处理成败都回 "success"，避免微信重试轰炸。
     * 安全模式（http 回调地址强制）：body 只有 Encrypt 密文，先验 msg_signature 再 AES 解密；
     * 明文/兼容模式：直接验 signature 后解析。
     */
    @PostMapping(value = "/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam String signature, @RequestParam String timestamp,
                           @RequestParam String nonce,
                           @RequestParam(value = "msg_signature", required = false) String msgSignature,
                           @RequestBody String body) {
        String payload = body == null ? "" : body.trim();
        try {
            String encrypt = extractEncrypt(payload);
            if (encrypt != null && !encrypt.isBlank()) {
                requireValidMsgSignature(msgSignature, timestamp, nonce, encrypt);
                payload = decrypt(encrypt);
            } else {
                requireValidSignature(signature, timestamp, nonce);
            }
            MediaEvent event = parseEvent(payload);
            if ("wxa_media_check".equalsIgnoreCase(event.event())) {
                handleMediaCheck(event);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理微信消息推送失败 body={}", body, e);
        }
        return "success";
    }

    /** 从 JSON 或 XML 推送体里取 Encrypt 密文；没有则返回 null（明文模式） */
    private String extractEncrypt(String payload) {
        try {
            if (payload.startsWith("<")) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setExpandEntityReferences(false);
                Document doc = factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
                return tagText(doc, "Encrypt");
            }
            JsonNode node = objectMapper.readTree(payload);
            return node.path("Encrypt").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 安全模式签名：sha1(sort(token, timestamp, nonce, encrypt)) */
    private void requireValidMsgSignature(String msgSignature, String timestamp, String nonce, String encrypt) {
        String token = wxProps.getMediaToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(403, "未配置 WX_MEDIA_TOKEN，回调接口未启用");
        }
        String[] arr = {token, timestamp, nonce, encrypt};
        Arrays.sort(arr);
        String sha1 = sha1(String.join("", arr));
        if (msgSignature == null || !MessageDigest.isEqual(
                sha1.getBytes(StandardCharsets.UTF_8),
                msgSignature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(403, "密文签名校验失败");
        }
    }

    /**
     * 微信回调密文解密：AES-256-CBC，key = Base64(AESKey + "=")，iv = key 前 16 字节；
     * 明文结构 = 16 字节随机串 + 4 字节网络序长度 + 消息体 + appid，PKCS7 填充。
     */
    private String decrypt(String encrypt) throws Exception {
        String aesKey = wxProps.getMediaAesKey();
        if (aesKey == null || aesKey.isBlank()) {
            throw new BusinessException(403, "未配置 WX_MEDIA_AES_KEY，无法解密安全模式回调");
        }
        byte[] key = Base64.getDecoder().decode(aesKey + "=");
        byte[] iv = Arrays.copyOfRange(key, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encrypt));
        int pad = plain[plain.length - 1] & 0xFF;
        if (pad < 1 || pad > 32) {
            pad = 0;
        }
        byte[] content = Arrays.copyOfRange(plain, 16, plain.length - pad);
        int len = ((content[0] & 0xFF) << 24) | ((content[1] & 0xFF) << 16)
                | ((content[2] & 0xFF) << 8) | (content[3] & 0xFF);
        return new String(content, 4, len, StandardCharsets.UTF_8);
    }

    private void handleMediaCheck(MediaEvent event) {
        if (event.traceId() == null || event.traceId().isBlank()) {
            return;
        }
        MediaCheck mc = mediaCheckMapper.selectOne(new LambdaQueryWrapper<MediaCheck>()
                .eq(MediaCheck::getTraceId, event.traceId()));
        if (mc == null) {
            log.warn("收到未知 trace_id 的审核回调: {}", event.traceId());
            return;
        }
        if (event.errcode() != 0) {
            mc.setStatus(3);
            mediaCheckMapper.updateById(mc);
            log.warn("头像审核检测失败 trace={} errcode={}", event.traceId(), event.errcode());
            return;
        }

        mc.setSuggest(event.suggest());
        if ("risky".equalsIgnoreCase(event.suggest())) {
            mc.setStatus(2);
            resetAvatarIfMatch(mc);
        } else {
            // pass 直接通过；review 保留头像但落库 suggest，供后台人工复核
            mc.setStatus(1);
        }
        mediaCheckMapper.updateById(mc);
    }

    /** 用户当前头像仍是这张违规图时才重置，避免误伤后来换的新头像 */
    private void resetAvatarIfMatch(MediaCheck mc) {
        User user = userMapper.selectById(mc.getUserId());
        if (user == null || user.getAvatar() == null || !user.getAvatar().contains(mc.getMediaPath())) {
            return;
        }
        user.setAvatar("");
        userMapper.updateById(user);
        log.info("头像违规已重置 userId={} path={} label-suggest={}",
                mc.getUserId(), mc.getMediaPath(), mc.getSuggest());
    }

    /** 兼容 JSON 与 XML 两种推送格式，统一抽成四个字段 */
    private MediaEvent parseEvent(String body) throws Exception {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("<")) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 关 DTD / 外部实体，防 XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)));
            return new MediaEvent(
                    tagText(doc, "Event"),
                    tagText(doc, "trace_id"),
                    parseInt(tagText(doc, "errcode")),
                    tagText(doc, "suggest"));
        }
        JsonNode node = objectMapper.readTree(trimmed);
        return new MediaEvent(
                node.path("Event").asText(""),
                node.path("trace_id").asText(null),
                node.path("errcode").asInt(0),
                node.path("result").path("suggest").asText(""));
    }

    private static String tagText(Document doc, String tag) {
        var list = doc.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent();
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void requireValidSignature(String signature, String timestamp, String nonce) {
        String token = wxProps.getMediaToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(403, "未配置 WX_MEDIA_TOKEN，回调接口未启用");
        }
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        String sha1 = sha1(String.join("", arr));
        // 常量时间比较，防时序侧信道；微信签名为小写 hex
        if (signature == null || !MessageDigest.isEqual(
                sha1.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(403, "签名校验失败");
        }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record MediaEvent(String event, String traceId, int errcode, String suggest) {
    }
}