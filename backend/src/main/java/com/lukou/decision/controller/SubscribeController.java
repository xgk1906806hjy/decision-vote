package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.SubscribeStatusVO;
import com.lukou.decision.service.SubscribeService;
import com.lukou.decision.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订阅消息额度。前端在 wx.requestSubscribeMessage 返回 accept 后调用 POST 记账。
 */
@RestController
@RequestMapping("/api/subscribe")
@RequiredArgsConstructor
public class SubscribeController {

    private final SubscribeService subscribeService;

    @GetMapping("/follow-up")
    public R<SubscribeStatusVO> status() {
        return R.ok(subscribeService.status(UserContext.require()));
    }

    @PostMapping("/follow-up")
    public R<SubscribeStatusVO> grant() {
        return R.ok(subscribeService.grant(UserContext.require()));
    }
}
