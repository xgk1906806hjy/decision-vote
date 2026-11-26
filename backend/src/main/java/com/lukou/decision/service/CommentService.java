package com.lukou.decision.service;

import com.lukou.decision.common.BusinessException;
import com.lukou.decision.dto.CommentReq;
import com.lukou.decision.dto.CommentVO;
import com.lukou.decision.entity.Comment;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.CommentMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Set<String> TAGS = Set.of("worth", "regret", "watching");

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final ContentAuditService audit;

    @Transactional
    public CommentVO create(Long postId, CommentReq req, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "这个话题不存在");
        }
        if (req.getOutcomeTag() != null && !TAGS.contains(req.getOutcomeTag())) {
            throw new BusinessException("结果标签不合法");
        }
        audit.check(req.getContent());

        Comment c = new Comment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setOptionId(req.getOptionId());
        c.setContent(req.getContent().trim());
        c.setOutcomeTag(req.getOutcomeTag());
        c.setStatus(1);
        commentMapper.insert(c);

        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        User u = userMapper.selectById(userId);
        vo.setUserName(u == null ? "路过的旅人" : u.getNickname());
        vo.setAvatar(u == null ? null : u.getAvatar());
        vo.setContent(c.getContent());
        vo.setOutcomeTag(c.getOutcomeTag());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }
}
