package com.lukou.decision.controller;

import com.lukou.decision.common.BusinessException;
import com.lukou.decision.common.R;
import com.lukou.decision.config.AdminProperties;
import com.lukou.decision.dto.ReminderCandidateVO;
import com.lukou.decision.service.FollowUpReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 回访提醒运维入口：先预览候选，确认无误再手动触发发送。
 */
@RestController
@RequestMapping("/api/admin/reminders")
@RequiredArgsConstructor
public class AdminReminderController {

    private final FollowUpReminderService followUpReminderService;
    private final AdminProperties adminProperties;

    @GetMapping
    public R<List<ReminderCandidateVO>> preview(
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        checkToken(token);
        return R.ok(followUpReminderService.preview(limit));
    }

    @PostMapping("/run")
    public R<FollowUpReminderService.RunResult> run(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        checkToken(token);
        return R.ok(followUpReminderService.runOnce());
    }

    private void checkToken(String token) {
        String expected = adminProperties.getToken();
        if (expected == null || expected.isBlank() || token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "管理员鉴权失败");
        }
    }
}
