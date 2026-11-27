package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.dto.SolutionReq;
import com.lukou.decision.dto.SolutionVO;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.Solution;
import com.lukou.decision.entity.SolutionLike;
import com.lukou.decision.entity.User;
import com.lukou.decision.entity.UserCredit;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.SolutionLikeMapper;
import com.lukou.decision.mapper.SolutionMapper;
import com.lukou.decision.mapper.UserCreditMapper;
import com.lukou.decision.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolutionService {

    private final SolutionMapper solutionMapper;
    private final SolutionLikeMapper likeMapper;
    private final UserCreditMapper creditMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final ContentAuditService audit;

    @Transactional
    public Long create(Long postId, SolutionReq req, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "这个话题不存在");
        }
        audit.check(req.getContent());
        Solution s = new Solution();
        s.setPostId(postId);
        s.setUserId(userId);
        s.setContent(req.getContent().trim());
        s.setAdopted(0);
        s.setLikeCount(0);
        s.setStatus(1);
        solutionMapper.insert(s);
        return s.getId();
    }

    /**
     * 方案列表：采纳优先，其次按点赞数。点赞状态和作者标签一并组装。
     */
    public List<SolutionVO> list(Long postId, Long viewerId) {
        List<Solution> solutions = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getPostId, postId)
                .eq(Solution::getStatus, 1)
                .orderByDesc(Solution::getAdopted)
                .orderByDesc(Solution::getLikeCount)
                .orderByDesc(Solution::getCreatedAt));
        if (solutions.isEmpty()) {
            return List.of();
        }
        return solutions.stream()
                .map(s -> toVO(s, viewerId))
                // 已采纳置顶，其余按点赞数降序；稳定排序会保留 SQL 的 createdAt 次序
                .sorted(Comparator.comparing(SolutionVO::isAdopted).reversed()
                        .thenComparing(SolutionVO::getLikeCount, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private SolutionVO toVO(Solution s, Long viewerId) {
        SolutionVO vo = new SolutionVO();
        vo.setId(s.getId());
        vo.setContent(s.getContent());
        vo.setLikeCount(s.getLikeCount() == null ? 0 : s.getLikeCount());
        vo.setAdopted(s.getAdopted() != null && s.getAdopted() == 1);
        vo.setMine(Objects.equals(s.getUserId(), viewerId));
        vo.setCreatedAt(s.getCreatedAt());

        User author = userMapper.selectById(s.getUserId());
        vo.setAuthorName(author == null ? "路过的旅人" : author.getNickname());
        vo.setAuthorAvatar(author == null ? null : author.getAvatar());

        List<UserCredit> credits = creditMapper.selectList(new LambdaQueryWrapper<UserCredit>()
                .eq(UserCredit::getUserId, s.getUserId()));
        int totalStars = credits.stream().mapToInt(c -> c.getStars() == null ? 0 : c.getStars()).sum();
        vo.setAuthorStars(totalStars);
        vo.setAuthorSceneTag(pickTopScene(credits));

        if (viewerId != null) {
            Long liked = likeMapper.selectCount(new LambdaQueryWrapper<SolutionLike>()
                    .eq(SolutionLike::getSolutionId, s.getId())
                    .eq(SolutionLike::getUserId, viewerId));
            vo.setLiked(liked != null && liked > 0);
        } else {
            vo.setLiked(false);
        }
        return vo;
    }

    private String pickTopScene(List<UserCredit> credits) {
        if (credits == null || credits.isEmpty()) {
            return null;
        }
        return credits.stream()
                .filter(c -> c.getStars() != null && c.getStars() > 0)
                .max(Comparator.comparingInt(UserCredit::getStars))
                .map(UserCredit::getScene)
                .orElse(null);
    }

    @Transactional
    public void like(Long solutionId, Long userId) {
        Solution s = solutionMapper.selectById(solutionId);
        if (s == null || (s.getStatus() != null && s.getStatus() == 0)) {
            throw new BusinessException(404, "方案不存在");
        }
        SolutionLike like = new SolutionLike();
        like.setSolutionId(solutionId);
        like.setUserId(userId);
        try {
            likeMapper.insert(like);
            solutionMapper.incrementLike(solutionId);
        } catch (DuplicateKeyException e) {
            likeMapper.delete(new LambdaQueryWrapper<SolutionLike>()
                    .eq(SolutionLike::getSolutionId, solutionId)
                    .eq(SolutionLike::getUserId, userId));
            solutionMapper.decrementLike(solutionId);
        }
    }

    /**
     * 采纳：仅发起者可操作。采纳后给方案作者加一颗星，并按帖子场景累计系统标签。
     * 每个帖子最多采纳一个方案；重复采纳同一方案视为取消采纳。
     */
    @Transactional
    public void adopt(Long postId, Long solutionId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "这个话题不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException(403, "只有发起人可以采纳方案");
        }
        Solution target = solutionMapper.selectById(solutionId);
        if (target == null || !Objects.equals(target.getPostId(), postId)
                || (target.getStatus() != null && target.getStatus() == 0)) {
            throw new BusinessException(404, "方案不存在");
        }

        // 取消该帖下已有的采纳
       List<Solution> adoptedList = solutionMapper.selectList(new LambdaQueryWrapper<Solution>()
                .eq(Solution::getPostId, postId).eq(Solution::getAdopted, 1));
        for (Solution old : adoptedList) {
            if (!Objects.equals(old.getId(), solutionId)) {
                old.setAdopted(0);
                solutionMapper.updateById(old);
                revokeCredit(old.getUserId(), SceneService.primary(post.getScene()));
            }
        }

        if (target.getAdopted() != null && target.getAdopted() == 1) {
            target.setAdopted(0);
            solutionMapper.updateById(target);
            revokeCredit(target.getUserId(), SceneService.primary(post.getScene()));
            return;
        }

        target.setAdopted(1);
        solutionMapper.updateById(target);
        awardCredit(target.getUserId(), SceneService.primary(post.getScene()));
    }

    private void awardCredit(Long authorId, String scene) {
        String key = (scene == null || scene.isBlank()) ? "其他" : scene.trim();
        UserCredit existing = creditMapper.selectOne(new LambdaQueryWrapper<UserCredit>()
                .eq(UserCredit::getUserId, authorId).eq(UserCredit::getScene, key).last("limit 1"));
        if (existing == null) {
            UserCredit c = new UserCredit();
            c.setUserId(authorId);
            c.setScene(key);
            c.setStars(1);
            creditMapper.insert(c);
        } else {
            existing.setStars((existing.getStars() == null ? 0 : existing.getStars()) + 1);
            creditMapper.updateById(existing);
        }
    }

    private void revokeCredit(Long authorId, String scene) {
        String key = (scene == null || scene.isBlank()) ? "其他" : scene.trim();
        UserCredit existing = creditMapper.selectOne(new LambdaQueryWrapper<UserCredit>()
                .eq(UserCredit::getUserId, authorId).eq(UserCredit::getScene, key).last("limit 1"));
        if (existing == null) {
            return;
        }
        int next = (existing.getStars() == null ? 0 : existing.getStars()) - 1;
        if (next <= 0) {
            creditMapper.deleteById(existing.getId());
        } else {
            existing.setStars(next);
            creditMapper.updateById(existing);
        }
    }
}
