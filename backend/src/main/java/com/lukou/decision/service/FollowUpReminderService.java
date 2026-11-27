package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.config.SubscribeProperties;
import com.lukou.decision.config.WxProperties;
import com.lukou.decision.dto.ReminderCandidateVO;
import com.lukou.decision.entity.FollowUp;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.FollowUpMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回访订阅消息：把"到期但没填结局"的决策主动推到用户微信上，
 * 而不是等他自己想起来点进「我的」页。
 *
 * <p>候选口径与 {@link FollowUpService#pending} 保持一致（answered_at 为空、due_at 已到），
 * 额外限制只提醒最近 N 天内到期的，避免一次给老用户翻出一堆旧账。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpReminderService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FollowUpMapper followUpMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final SubscribeService subscribeService;
    private final WxApiService wxApiService;
    private final SubscribeProperties props;
    private final WxProperties wxProperties;

    /**
     * 预览本轮会提醒哪些人（不发送、不消耗额度），供管理端核对。
     */
    public List<ReminderCandidateVO> preview(Integer limit) {
        int size = limit == null || limit <= 0 ? props.getBatchSize() : Math.min(limit, 200);
        List<ReminderCandidateVO> result = new ArrayList<>();
        for (FollowUp fu : dueList(size)) {
            Post post = postMapper.selectById(fu.getPostId());
            ReminderCandidateVO vo = new ReminderCandidateVO();
            vo.setPostId(fu.getPostId());
            vo.setUserId(fu.getUserId());
            vo.setTitle(post == null ? "（已删除）" : post.getTitle());
            vo.setDueAt(fu.getDueAt());
            vo.setRemain(subscribeService.remain(fu.getUserId()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 执行一轮发送。返回统计结果，便于定时任务日志与管理端手动触发。
     */
    public RunResult runOnce() {
        if (!subscribeService.isTemplateReady()) {
            log.info("回访提醒跳过：未配置 app.subscribe.follow-up-template-id");
            return new RunResult("template-not-configured", 0, 0, 0, 0);
        }
        // dry-run 只打印不发送，不需要 access_token；因此放在 mock 判断之前，
        // 这样本地（WX_MOCK=true）也能把候选筛选、额度扣减之外的链路跑一遍。
        if (!props.isDryRun() && wxProperties.isMock()) {
            log.info("回访提醒跳过：WX_MOCK=true 时无法获取 access_token");
            return new RunResult("wx-mock", 0, 0, 0, 0);
        }

        List<FollowUp> list = dueList(props.getBatchSize());
        if (list.isEmpty()) {
            return new RunResult("ok", 0, 0, 0, 0);
        }

        List<Long> postIds = list.stream().map(FollowUp::getPostId).distinct().collect(Collectors.toList());
        List<Long> userIds = list.stream().map(FollowUp::getUserId).distinct().collect(Collectors.toList());
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
        Map<Long, String> openidMap = userMapper.selectBatchIds(userIds).stream()
                .filter(u -> u.getOpenid() != null && !u.getOpenid().isBlank())
                .collect(Collectors.toMap(User::getId, User::getOpenid, (a, b) -> a));

        int sent = 0, noQuota = 0, failed = 0, skipped = 0;
        for (FollowUp fu : list) {
            Post post = postMap.get(fu.getPostId());
            if (post == null) {
                // 帖子已删或已隐藏，标记掉避免每轮重复扫描
                markReminded(fu.getId());
                skipped++;
                continue;
            }
            String openid = openidMap.get(fu.getUserId());
            if (openid == null) {
                markReminded(fu.getId());
                skipped++;
                continue;
            }
            if (subscribeService.remain(fu.getUserId()) <= 0) {
                // 不标记：用户下次授权后这一条还能被推出去
                noQuota++;
                continue;
            }

            Map<String, Object> data = buildData(post, fu);
            if (props.isDryRun()) {
                log.info("[dry-run] 将提醒 userId={} postId={} title={}", fu.getUserId(), post.getId(), post.getTitle());
                skipped++;
                continue;
            }

            WxApiService.SendResult result = wxApiService.sendSubscribeMessage(
                    openid, props.getFollowUpTemplateId(), buildPage(post.getId()), data);
            if (result.ok()) {
                subscribeService.consume(fu.getUserId());
                markReminded(fu.getId());
                sent++;
            } else if (result.shouldStopReminding()) {
                // 用户拒收或模板参数错误：作废额度并标记，避免每轮都撞同一面墙
                subscribeService.clear(fu.getUserId());
                markReminded(fu.getId());
                failed++;
            } else {
                failed++;
            }
        }
        log.info("回访提醒完成：候选 {} 条，发送 {}，无额度 {}，跳过 {}，失败 {}",
                list.size(), sent, noQuota, skipped, failed);
        return new RunResult("ok", list.size(), sent, noQuota, failed);
    }

    /**
     * 微信模板字段：thing1 = 决策标题，time2 = 计划回访时间，thing3 = 引导语。
     * 模板字段名需与公众平台「订阅消息」里保存的模板一致，换模板时改这里。
     */
    private Map<String, Object> buildData(Post post, FollowUp fu) {
        Map<String, Object> data = new HashMap<>();
        data.put("thing1", Map.of("value", clip(post.getTitle(), 20)));
        data.put("time2", Map.of("value", fu.getDueAt().format(TIME_FMT)));
        data.put("thing3", Map.of("value", "回来用 1 分钟填写结局，帮到后来的人"));
        return data;
    }

    private String buildPage(Long postId) {
        String page = props.getFollowUpPage();
        if (page == null || page.isBlank()) {
            return "/pages/detail/detail?id=" + postId;
        }
        return page.replace("{postId}", String.valueOf(postId));
    }

    /** thing 类型字段限 20 个字符，超出微信直接报 47003 */
    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private List<FollowUp> dueList(int limit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime earliest = now.minusDays(Math.max(1, props.getMaxAgeDays()));
        return followUpMapper.selectList(new LambdaQueryWrapper<FollowUp>()
                .isNull(FollowUp::getAnsweredAt)
                .isNull(FollowUp::getRemindedAt)
                .isNotNull(FollowUp::getDueAt)
                .le(FollowUp::getDueAt, now)
                .ge(FollowUp::getDueAt, earliest)
                .orderByAsc(FollowUp::getDueAt)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    private void markReminded(Long followUpId) {
        FollowUp update = new FollowUp();
        update.setId(followUpId);
        update.setRemindedAt(LocalDateTime.now());
        followUpMapper.updateById(update);
    }

    public record RunResult(String status, int candidates, int sent, int noQuota, int failed) {
    }
}
