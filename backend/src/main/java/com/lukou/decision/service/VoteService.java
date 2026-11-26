package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.entity.Option;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.Vote;
import com.lukou.decision.mapper.OptionMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.VoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteService {

    private final VoteMapper voteMapper;
    private final PostMapper postMapper;
    private final OptionMapper optionMapper;

    @Transactional
    public void vote(Long postId, Long optionId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "这个话题不存在");
        }
        if ("closed".equals(post.getStatus())) {
            throw new BusinessException("这个话题已经结束投票了");
        }
        if (post.getDeadline() != null && java.time.LocalDateTime.now().isAfter(post.getDeadline())) {
            throw new BusinessException("这个话题已经结束投票了");
        }
        Option option = optionMapper.selectById(optionId);
        if (option == null || !option.getPostId().equals(postId)) {
            throw new BusinessException("选项不存在");
        }

        Vote vote = new Vote();
        vote.setPostId(postId);
        vote.setOptionId(optionId);
        vote.setUserId(userId);
        try {
            voteMapper.insert(vote);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("你已经投过票了");
        }
    }
}
