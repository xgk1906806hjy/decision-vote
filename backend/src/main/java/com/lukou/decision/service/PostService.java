package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.common.PageResult;
import com.lukou.decision.dto.*;
import com.lukou.decision.entity.*;
import com.lukou.decision.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final OptionMapper optionMapper;
    private final VoteMapper voteMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final FollowUpMapper followUpMapper;
    private final ContentAuditService audit;
    private final SceneService sceneService;

    @Transactional
    public Long create(CreatePostReq req, Long userId) {
        List<String> options = req.getOptions() == null ? List.of() : req.getOptions();
        List<String> clean = options.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (clean.size() < 2) {
            throw new BusinessException("至少要有两个不同的选项");
        }
        if (clean.size() > 6) {
            throw new BusinessException("选项最多 6 个");
        }
        if (clean.stream().anyMatch(s -> s.length() > 40)) {
            throw new BusinessException("单个选项最多 40 字");
        }
        // 场景标签：预设与自定义都走同一条路，只校验长度与内容
        String scene = sceneService.normalize(req.getScene());
        audit.check(scene);
        audit.check(req.getTitle());
        audit.check(req.getContent());
        clean.forEach(audit::check);

        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(req.getTitle().trim());
        post.setContent(req.getContent().trim());
        post.setScene(scene);
        post.setAnonymous(req.getAnonymous() == null ? 0 : req.getAnonymous());
        post.setStatus("open");
        post.setViewCount(0);
        if (req.getDeadlineHours() != null && req.getDeadlineHours() > 0) {
            post.setDeadline(LocalDateTime.now().plusHours(req.getDeadlineHours()));
        }
        postMapper.insert(post);

        int sort = 0;
        for (String label : clean) {
            Option o = new Option();
            o.setPostId(post.getId());
            o.setLabel(label);
            o.setSortNo(sort++);
            optionMapper.insert(o);
        }

        // 建一条回访计划：三个月后提醒帖主回来填结果
        FollowUp fu = new FollowUp();
        fu.setPostId(post.getId());
        fu.setUserId(userId);
        fu.setDueAt(LocalDateTime.now().plusMonths(3));
        followUpMapper.insert(fu);

        return post.getId();
    }

    public PageResult<PostCardVO> feed(int page, int size, String scene, Long userId) {
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isBlank()) {
            qw.eq(Post::getScene, scene);
        }
        qw.orderByDesc(Post::getCreatedAt);

        Page<Post> p = postMapper.selectPage(new Page<>(page, size), qw);
        List<PostCardVO> cards = buildCards(p.getRecords(), userId);
        return new PageResult<>(cards, p.getTotal(), page, size, page * size < p.getTotal());
    }

    public PostDetailVO detail(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "这个话题不存在或已删除");
        }
        postMapper.update(null, new UpdateWrapper<Post>()
                .eq("id", postId)
                .setSql("view_count = view_count + 1"));

        PostCardVO card = buildCards(List.of(post), userId).get(0);
        PostDetailVO vo = new PostDetailVO();
        copy(card, vo);
        vo.setContent(post.getContent());
        vo.setAuthor(Objects.equals(post.getUserId(), userId));

        // 评论
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, 1)
                .orderByDesc(Comment::getCreatedAt)
                .last("limit 100"));
        vo.setComments(buildComments(comments));

        // 回访结果
        FollowUp fu = followUpMapper.selectOne(new LambdaQueryWrapper<FollowUp>()
                .eq(FollowUp::getPostId, postId).last("limit 1"));
        if (fu != null && fu.getAnsweredAt() != null) {
            vo.setChosenOptionId(fu.getChoseOptionId());
            vo.setOutcome(fu.getOutcome());
            vo.setOutcomeNote(fu.getNote());
            vo.setOutcomeSlot(fu.getCreatedAt() == null ? null : "已回访");
        }
        return vo;
    }

    public PageResult<PostCardVO> myPosts(Long userId, int page, int size) {
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .orderByDesc(Post::getCreatedAt);
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), qw);
        return new PageResult<>(buildCards(p.getRecords(), userId), p.getTotal(), page, size,
                page * size < p.getTotal());
    }

    public PageResult<PostCardVO> myVoted(Long userId, int page, int size) {
        Page<Vote> vp = voteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Vote>().eq(Vote::getUserId, userId).orderByDesc(Vote::getCreatedAt));
        List<Long> postIds = vp.getRecords().stream().map(Vote::getPostId).collect(Collectors.toList());
        if (postIds.isEmpty()) {
            return new PageResult<>(List.of(), vp.getTotal(), page, size, false);
        }
        List<Post> posts = postMapper.selectBatchIds(postIds);
        Map<Long, Post> order = posts.stream().collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> sorted = postIds.stream().map(order::get).filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageResult<>(buildCards(sorted, userId), vp.getTotal(), page, size,
                page * size < vp.getTotal());
    }

    // ---------------- 内部组装 ----------------

    private List<PostCardVO> buildCards(List<Post> posts, Long userId) {
        if (posts == null || posts.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        Map<Long, List<Option>> optionMap = optionMapper.selectList(new LambdaQueryWrapper<Option>()
                        .in(Option::getPostId, postIds).orderByAsc(Option::getSortNo))
                .stream().collect(Collectors.groupingBy(Option::getPostId));

        Map<Long, List<VoteStatRow>> statMap = voteMapper.statByPostIds(postIds)
                .stream().collect(Collectors.groupingBy(VoteStatRow::getPostId));

        Map<Long, Integer> commentMap = commentMapper.countByPostIds(postIds)
                .stream().collect(Collectors.toMap(CountRow::getPostId, CountRow::getCnt, (a, b) -> a));

        Map<Long, Long> myVoteMap = new HashMap<>();
        if (userId != null) {
            voteMapper.selectList(new LambdaQueryWrapper<Vote>()
                            .in(Vote::getPostId, postIds).eq(Vote::getUserId, userId))
                    .forEach(v -> myVoteMap.put(v.getPostId(), v.getOptionId()));
        }

        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<PostCardVO> result = new ArrayList<>();
        for (Post post : posts) {
            PostCardVO vo = new PostCardVO();
            vo.setId(post.getId());
            vo.setTitle(post.getTitle());
            vo.setSummary(truncate(post.getContent(), 60));
            vo.setScene(post.getScene());
            boolean anonymous = post.getAnonymous() != null && post.getAnonymous() == 1;
            vo.setAnonymous(anonymous);
            User author = userMap.get(post.getUserId());
            if (anonymous) {
                vo.setAuthorName("匿名旅人");
                vo.setAuthorAvatar(null);
            } else {
                vo.setAuthorName(author == null ? "路过的旅人" : author.getNickname());
                vo.setAuthorAvatar(author == null ? null : author.getAvatar());
            }
            vo.setCreatedAt(post.getCreatedAt());
            vo.setDeadline(post.getDeadline());
            vo.setStatus(effectiveStatus(post));

            List<VoteStatRow> rows = statMap.getOrDefault(post.getId(), List.of());
            Map<Long, Integer> cntById = rows.stream()
                    .collect(Collectors.toMap(VoteStatRow::getOptionId, VoteStatRow::getCnt, Integer::sum));
            int total = cntById.values().stream().mapToInt(Integer::intValue).sum();

            // 盲投：未投票且未截止时，只暴露参与人数，不暴露分布
            boolean revealed = (userId != null && Objects.equals(post.getUserId(), userId))
                    || (userId != null && myVoteMap.containsKey(post.getId()))
                    || "closed".equals(vo.getStatus());

            List<OptionVO> optionVOs = new ArrayList<>();
            for (Option o : optionMap.getOrDefault(post.getId(), List.of())) {
                OptionVO ov = new OptionVO();
                ov.setId(o.getId());
                ov.setLabel(o.getLabel());
                if (revealed) {
                    int c = cntById.getOrDefault(o.getId(), 0);
                    ov.setCount(c);
                    ov.setPercent(total == 0 ? 0 : Math.round(c * 100f / total));
                } else {
                    ov.setCount(0);
                    ov.setPercent(0);
                }
                optionVOs.add(ov);
            }
            vo.setOptions(optionVOs);
            vo.setTotalVoteCount(total);
            vo.setVoteCount(total);
            vo.setBlind(!revealed);
            vo.setCommentCount(commentMap.getOrDefault(post.getId(), 0));
            vo.setMyOptionId(myVoteMap.get(post.getId()));
            result.add(vo);
        }
        return result;
    }

    private List<CommentVO> buildComments(List<Comment> comments) {
        if (comments.isEmpty()) {
            return new ArrayList<>();
        }
        // 过来人的建议优先展示；同身份内保持时间倒序
        List<Comment> ordered = new ArrayList<>(comments);
        ordered.sort(Comparator
                .comparing((Comment c) -> "experienced".equals(c.getAuthorType()) ? 0 : 1)
                .thenComparing(Comment::getCreatedAt, Comparator.reverseOrder()));

        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Long> optionIds = comments.stream().map(Comment::getOptionId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> optionLabel = optionIds.isEmpty() ? Map.of()
                : optionMapper.selectBatchIds(optionIds).stream()
                .collect(Collectors.toMap(Option::getId, Option::getLabel));

        List<CommentVO> list = new ArrayList<>();
        for (Comment c : ordered) {
            CommentVO vo = new CommentVO();
            vo.setId(c.getId());
            User u = userMap.get(c.getUserId());
            vo.setUserName(u == null ? "路过的旅人" : u.getNickname());
            vo.setAvatar(u == null ? null : u.getAvatar());
            vo.setContent(c.getContent());
            vo.setOutcomeTag(c.getOutcomeTag());
            vo.setAuthorType(c.getAuthorType());
            vo.setOptionLabel(c.getOptionId() == null ? null : optionLabel.get(c.getOptionId()));
            vo.setCreatedAt(c.getCreatedAt());
            list.add(vo);
        }
        return list;
    }

    private String effectiveStatus(Post post) {
        if (post.getDeadline() != null && LocalDateTime.now().isAfter(post.getDeadline())) {
            return "closed";
        }
        return post.getStatus();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private void copy(PostCardVO from, PostDetailVO to) {
        to.setId(from.getId());
        to.setTitle(from.getTitle());
        to.setSummary(from.getSummary());
        to.setScene(from.getScene());
        to.setAnonymous(from.isAnonymous());
        to.setAuthorName(from.getAuthorName());
        to.setAuthorAvatar(from.getAuthorAvatar());
        to.setVoteCount(from.getVoteCount());
        to.setCommentCount(from.getCommentCount());
        to.setStatus(from.getStatus());
        to.setDeadline(from.getDeadline());
        to.setCreatedAt(from.getCreatedAt());
        to.setOptions(from.getOptions());
        to.setMyOptionId(from.getMyOptionId());
        to.setTotalVoteCount(from.getTotalVoteCount());
        to.setBlind(from.isBlind());
    }
}
