package com.lukou.decision.service;

import com.lukou.decision.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容安全检查。
 * 生产环境应接入微信 security.msgSecCheck（需要 access_token，服务器出口 IP 需加入白名单）。
 * 这里先做本地关键词兜底，结构上留好了扩展位。
 */
@Service
public class ContentAuditService {

    private static final List<String> BLOCK_WORDS = List.of(
            "代开发票", "办证", "刷单", "赌博", "博彩", "色情", "裸聊",
            "加微信领", "私聊转账", "杀猪盘", "传销"
    );

    private static final List<String> REVIEW_WORDS = List.of(
            "傻逼", "去死", "智障", "滚蛋"
    );

    public void check(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
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
