package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.dto.FollowUpReq;
import com.lukou.decision.dto.PendingFollowUpVO;
import com.lukou.decision.entity.FollowUp;
import com.lukou.decision.entity.Post;
import com.lukou.decision.mapper.FollowUpMapper;
import com.lukou.decision.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowUpService {

    private static final Set<String> TAGS = Set.of("worth", "regret", "watching");

    private final FollowUpMapper followUpMapper;
    private final PostMapper postMapper;

    @Transactional
    public void submit(Long postId, FollowUpReq req, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "这个话题不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException(403, "只有发起人可以填写结果");
        }
        if (req.getOutcome() != null && !TAGS.contains(req.getOutcome())) {
            throw new BusinessException("结果标签不合法");
        }

        FollowUp fu = followUpMapper.selectOne(new LambdaQueryWrapper<FollowUp>()
                .eq(FollowUp::getPostId, postId).last("limit 1"));
        if (fu == null) {
            fu = new FollowUp();
            fu.setPostId(postId);
            fu.setUserId(userId);
            fu.setChoseOptionId(req.getChoseOptionId());
            fu.setOutcome(req.getOutcome());
            fu.setNote(req.getNote());
            fu.setAnsweredAt(LocalDateTime.now());
            followUpMapper.insert(fu);
        } else {
            fu.setChoseOptionId(req.getChoseOptionId());
            fu.setOutcome(req.getOutcome());
            fu.setNote(req.getNote());
            fu.setAnsweredAt(LocalDateTime.now());
            followUpMapper.updateById(fu);
        }
    }

    /**
     * 到期且还没填回访的帖子，用于「我的」页提醒。
     */
    public List<PendingFollowUpVO> pending(Long userId) {
        List<FollowUp> list = followUpMapper.selectList(new LambdaQueryWrapper<FollowUp>()
                .eq(FollowUp::getUserId, userId)
                .isNull(FollowUp::getAnsweredAt)
                .isNotNull(FollowUp::getDueAt)
                .le(FollowUp::getDueAt, LocalDateTime.now())
                .orderByAsc(FollowUp::getDueAt)
                .last("limit 20"));
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = list.stream().map(FollowUp::getPostId).collect(Collectors.toList());
        Map<Long, String> titleMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Post::getTitle, (a, b) -> a));

        List<PendingFollowUpVO> result = new ArrayList<>();
        for (FollowUp f : list) {
            PendingFollowUpVO vo = new PendingFollowUpVO();
            vo.setPostId(f.getPostId());
            vo.setTitle(titleMap.getOrDefault(f.getPostId(), "已删除的话题"));
            vo.setDueAt(f.getDueAt());
            result.add(vo);
        }
        return result;
    }
}
