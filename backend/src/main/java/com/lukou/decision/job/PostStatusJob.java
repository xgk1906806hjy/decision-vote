package com.lukou.decision.job;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lukou.decision.entity.Post;
import com.lukou.decision.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 把已过 deadline 的决策从 open 落库改为 closed。
 * 读取侧仍会按 deadline 兜底，因此任务短暂延迟不会影响投票校验；
 * 落库的意义是让后台统计、筛选和索引看到真实状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostStatusJob {

    private final PostMapper postMapper;

    @Scheduled(
            initialDelayString = "${app.post.close-initial-delay-ms:5000}",
            fixedDelayString = "${app.post.close-interval-ms:60000}"
    )
    public void closeExpiredPosts() {
        int updated = postMapper.update(null, new UpdateWrapper<Post>()
                .eq("status", "open")
                .isNotNull("deadline")
                .le("deadline", LocalDateTime.now())
                .set("status", "closed"));
        if (updated > 0) {
            log.info("已将 {} 条到期决策落库为 closed", updated);
        }
    }
}
