package com.lukou.decision.job;

import com.lukou.decision.service.FollowUpReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回访提醒定时任务。默认每天 10:00 跑一次——早上通勤时段点开微信的概率最高，
 * 凌晨推送只会被静音。任务本身是幂等的：已提醒过的记录会被 reminded_at 挡掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpReminderJob {

    private final FollowUpReminderService followUpReminderService;

    @Scheduled(cron = "${app.subscribe.cron:0 0 10 * * ?}")
    public void remind() {
        try {
            followUpReminderService.runOnce();
        } catch (Exception e) {
            log.error("回访提醒任务执行失败", e);
        }
    }
}
