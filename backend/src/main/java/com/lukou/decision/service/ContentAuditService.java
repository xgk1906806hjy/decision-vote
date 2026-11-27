package com.lukou.decision.service;

import com.lukou.decision.common.BusinessException;
import com.lukou.decision.config.ContentProperties;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.UserMapper;
import com.lukou.decision.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内容安全检查：本地词库兜底 + 微信 msgSecCheck。
 * audit-mock=true 时只走本地词库；生产环境置 false 后调用微信接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAuditService {

    private static final List<String> BLOCK_WORDS = List.of(
            "代开发票", "办证", "刷单", "赌博", "博彩", "色情", "裸聊",
            "加微信领", "私聊转账", "杀猪盘", "传销"
    );

    private static final List<String> REVIEW_WORDS = List.of(
            "傻逼", "去死", "智障", "滚蛋"
    );

    private final ContentProperties props;
    private final WxApiService wxApiService;
    private final UserMapper userMapper;
    private final Map<Long, String> openidCache = new ConcurrentHashMap<>();

    public void check(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        checkLocal(text);
        if (props.isAuditMock()) {
            return;
        }

        Long userId = UserContext.get();
        if (userId == null) {
            log.warn("内容安全检查缺少登录上下文，已降级为本地词库");
            return;
        }
        String openid = openidCache.computeIfAbsent(userId, id -> {
            User user = userMapper.selectById(id);
            return user == null || user.getOpenid() == null ? "" : user.getOpenid();
        });
        if (openid.isBlank()) {
            log.warn("用户 {} 没有 openid，内容安全检查已降级为本地词库", userId);
            return;
        }

        try {
            WxApiService.CheckResult result = wxApiService.msgSecCheck(text, openid, props.getScene());
            if (result.isRisky()) {
                throw new BusinessException("内容包含不适合发布的信息，请修改后再试");
            }
            if (result.isReview()) {
                throw new BusinessException("内容需要修改后再试");
            }
        } catch (BusinessException e) {
            if (e.getCode() == 500 && props.isFailOpen()) {
                log.error("微信内容安全接口不可用，按 fail-open 降级为本地词库", e);
                return;
            }
            throw e;
        }
    }

    private void checkLocal(String text) {
        String lower = text.toLowerCase();
        for (String w : BLOCK_WORDS) {
            if (lower.contains(w)) {
                throw new BusinessException("内容包含不适合发布的信息，请修改后再试");
            }
        }
        for (String w : REVIEW_WORDS) {
            if (lower.contains(w)) {
                throw new BusinessException("请注意用语，友善的交流更容易被采纳");
            }
        }
    }
}
