package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.common.PageResult;
import com.lukou.decision.dto.CaseStatsVO;
import com.lukou.decision.dto.CaseVO;
import com.lukou.decision.dto.SceneStatVO;
import com.lukou.decision.entity.FollowUp;
import com.lukou.decision.entity.Option;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.Vote;
import com.lukou.decision.mapper.CommentMapper;
import com.lukou.decision.mapper.FollowUpMapper;
import com.lukou.decision.mapper.OptionMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.VoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaseService {

    private static final Map<String, String> OUTCOME_LABEL = Map.of(
            "worth", "值得", "regret", "后悔", "watching", "观察中");

    private final FollowUpMapper followUpMapper;
    private final PostMapper postMapper;
    private final OptionMapper optionMapper;
    private final VoteMapper voteMapper;
    private final CommentMapper commentMapper;

    /**
     * 案例库：已回访（有结局）的决策，按场景和关键词筛选。
     */
    public PageResult<CaseVO> list(int page, int size, String scene, String keyword) {
        List<FollowUp> answered = followUpMapper.selectList(new LambdaQueryWrapper<FollowUp>()
                .isNotNull(FollowUp::getAnsweredAt)
                .orderByDesc(FollowUp::getAnsweredAt));
        if (answered.isEmpty()) {
            return new PageResult<>(List.of(), 0, page, size, false);
        }

        List<Long> postIds = answered.stream().map(FollowUp::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

        List<CaseVO> all = new ArrayList<>();
        for (FollowUp fu : answered) {
            Post post = postMap.get(fu.getPostId());
            if (post == null || post.getDeleted() != null && post.getDeleted() == 1) {
                continue;
            }
            if (scene != null && !scene.isBlank() && !scene.equals(post.getScene())) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()
                    && !post.getTitle().contains(keyword) && !post.getContent().contains(keyword)) {
                continue;
            }
            all.add(toCase(post, fu, 0));
        }

        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageResult<>(all.subList(from, to), all.size(), page, size, to < all.size());
    }

    /**
     * 相似决策推荐：同场景的案例优先，其次按标题关键词重合度。
     */
    public List<CaseVO> similar(String scene, String keyword, int limit) {
        PageResult<CaseVO> base = list(1, 200, scene, null);
        List<CaseVO> cases = new ArrayList<>(base.getList());
        for (CaseVO c : cases) {
            int score = (scene != null && scene.equals(c.getScene())) ? 60 : 0;
            if (keyword != null && !keyword.isBlank() && c.getTitle() != null) {
                int hit = 0;
                for (char ch : keyword.toCharArray()) {
                    if (c.getTitle().indexOf(ch) >= 0) {
                        hit++;
                    }
                }
                score += Math.min(hit * 5, 40);
            }
            c.setSimilarity(score);
        }
        return cases.stream()
                .sorted(Comparator.comparingInt(CaseVO::getSimilarity).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 案例库聚合统计：当前场景（或全部）的结局分布 + 各场景案例量。
     * 只统计已回访且未删除的决策；outcome 为空的记录计入 total，不计入三类比例。
     */
    public CaseStatsVO stats(String scene) {
        List<FollowUp> answered = followUpMapper.selectList(new LambdaQueryWrapper<FollowUp>()
                .isNotNull(FollowUp::getAnsweredAt));
        CaseStatsVO result = new CaseStatsVO();
        result.setScenes(List.of());
        if (answered.isEmpty()) {
            return result;
        }

        List<Long> postIds = answered.stream().map(FollowUp::getPostId).collect(Collectors.toList());
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

        int total = 0, worth = 0, regret = 0, watching = 0, unknown = 0;
        Map<String, SceneStatVO> sceneMap = new LinkedHashMap<>();
        for (FollowUp fu : answered) {
            Post post = postMap.get(fu.getPostId());
            if (post == null || post.getDeleted() != null && post.getDeleted() == 1) {
                continue;
            }
            String key = post.getScene() == null || post.getScene().isBlank() ? "其他" : post.getScene();
            SceneStatVO s = sceneMap.computeIfAbsent(key, k -> {
                SceneStatVO vo = new SceneStatVO();
                vo.setScene(k);
                return vo;
            });
            s.setTotal(s.getTotal() + 1);
            switch (fu.getOutcome() == null ? "" : fu.getOutcome()) {
                case "worth" -> { s.setWorth(s.getWorth() + 1); }
                case "regret" -> { s.setRegret(s.getRegret() + 1); }
                case "watching" -> { s.setWatching(s.getWatching() + 1); }
                default -> { }
            }

            if (scene == null || scene.isBlank() || scene.equals(post.getScene())) {
                total++;
                switch (fu.getOutcome() == null ? "" : fu.getOutcome()) {
                    case "worth" -> worth++;
                    case "regret" -> regret++;
                    case "watching" -> watching++;
                    default -> unknown++;
                }
            }
        }

        result.setTotal(total);
        result.setWorth(worth);
        result.setRegret(regret);
        result.setWatching(watching);
        result.setUnknown(unknown);
        int known = worth + regret + watching;
        result.setWorthRate(percent(worth, known));
        result.setRegretRate(percent(regret, known));
        result.setWatchingRate(percent(watching, known));

        for (SceneStatVO s : sceneMap.values()) {
            int sceneKnown = s.getWorth() + s.getRegret() + s.getWatching();
            s.setWorthRate(percent(s.getWorth(), sceneKnown));
            s.setRegretRate(percent(s.getRegret(), sceneKnown));
        }
        List<SceneStatVO> scenes = sceneMap.values().stream()
                .sorted(Comparator.comparingInt(SceneStatVO::getTotal).reversed())
                .limit(6)
                .collect(Collectors.toList());
        result.setScenes(scenes);
        return result;
    }

    private int percent(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }

    private CaseVO toCase(Post post, FollowUp fu, int similarity) {
        CaseVO vo = new CaseVO();
        vo.setPostId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setScene(post.getScene());
        vo.setOutcome(fu.getOutcome());
        vo.setOutcomeLabel(OUTCOME_LABEL.getOrDefault(fu.getOutcome(), "已有结局"));
        vo.setOutcomeNote(fu.getNote());
        vo.setAnsweredAt(fu.getAnsweredAt());
        vo.setSimilarity(similarity);

        if (fu.getChoseOptionId() != null) {
            Option chosen = optionMapper.selectById(fu.getChoseOptionId());
            vo.setChosenLabel(chosen == null ? null : chosen.getLabel());
        }

        List<Vote> votes = voteMapper.selectList(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getPostId, post.getId()));
        vo.setVoteCount(votes.size());

        Long comments = commentMapper.selectCount(new LambdaQueryWrapper<com.lukou.decision.entity.Comment>()
                .eq(com.lukou.decision.entity.Comment::getPostId, post.getId()));
        vo.setCommentCount(comments == null ? 0 : comments.intValue());
        return vo;
    }
}
