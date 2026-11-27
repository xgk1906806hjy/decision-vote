package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.common.PageResult;
import com.lukou.decision.dto.ReportVO;
import com.lukou.decision.entity.Comment;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.Report;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.CommentMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.ReportMapper;
import com.lukou.decision.mapper.UserMapper;
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

/**
 * 举报处置后台：待处理列表 + 隐藏/驳回。
 * 内容隐藏复用现有逻辑删除/status 字段，不新增独立审核状态。
 */
@Service
@RequiredArgsConstructor
public class ReportAdminService {

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;

    public PageResult<ReportVO> list(int handled, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreatedAt);
        if (handled == 0 || handled == 1) {
            qw.eq(Report::getHandled, handled);
        }

        Page<Report> p = reportMapper.selectPage(new Page<>(safePage, safeSize), qw);
        List<Report> records = p.getRecords();
        if (records.isEmpty()) {
            return new PageResult<>(List.of(), p.getTotal(), safePage, safeSize, false);
        }

        Set<Long> userIds = records.stream().map(Report::getUserId).collect(Collectors.toSet());
        Map<Long, String> names = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? "未知用户" : u.getNickname(), (a, b) -> a));

        Set<Long> postIds = records.stream()
                .filter(r -> "post".equals(r.getTargetType()))
                .map(Report::getTargetId).collect(Collectors.toSet());
        Map<Long, Post> posts = postIds.isEmpty() ? Map.of()
                : postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, pst -> pst, (a, b) -> a));

        Set<Long> commentIds = records.stream()
                .filter(r -> "comment".equals(r.getTargetType()))
                .map(Report::getTargetId).collect(Collectors.toSet());
        Map<Long, Comment> comments = commentIds.isEmpty() ? Map.of()
                : commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(Comment::getId, c -> c, (a, b) -> a));

        List<ReportVO> list = new ArrayList<>();
        for (Report r : records) {
            ReportVO vo = new ReportVO();
            vo.setId(r.getId());
            vo.setTargetType(r.getTargetType());
            vo.setTargetId(r.getTargetId());
            vo.setReason(r.getReason());
            vo.setHandled(r.getHandled());
            vo.setAction(r.getAction());
            vo.setHandledAt(r.getHandledAt());
            vo.setCreatedAt(r.getCreatedAt());
            vo.setReporterName(names.getOrDefault(r.getUserId(), "未知用户"));

            if ("post".equals(r.getTargetType())) {
                Post pst = posts.get(r.getTargetId());
                if (pst == null) {
                    vo.setTargetTitle("内容已删除");
                    vo.setTargetExcerpt("");
                    vo.setTargetStatus("已删除");
                } else {
                    vo.setPostId(pst.getId());
                    vo.setTargetTitle(pst.getTitle());
                    vo.setTargetExcerpt(truncate(pst.getContent(), 80));
                    vo.setTargetStatus(pst.getStatus());
                }
            } else {
                Comment c = comments.get(r.getTargetId());
                if (c == null) {
                    vo.setTargetTitle("建议已删除");
                    vo.setTargetExcerpt("");
                    vo.setTargetStatus("已删除");
                } else {
                    vo.setPostId(c.getPostId());
                    vo.setTargetTitle("建议 #" + c.getId());
                    vo.setTargetExcerpt(truncate(c.getContent(), 80));
                    vo.setTargetStatus(Objects.equals(c.getStatus(), 1) ? "正常" : "已隐藏");
                }
            }
            list.add(vo);
        }
        return new PageResult<>(list, p.getTotal(), safePage, safeSize,
                (long) safePage * safeSize < p.getTotal());
    }

    @Transactional
    public void handle(Long id, String action) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException(404, "举报记录不存在");
        }
        if (Objects.equals(report.getHandled(), 1)) {
            throw new BusinessException(400, "该举报已经处理过了");
        }
        if ("hide".equals(action)) {
            hideTarget(report.getTargetType(), report.getTargetId());
        } else if (!"dismiss".equals(action)) {
            throw new BusinessException(400, "处理动作只能是 hide 或 dismiss");
        }

        report.setHandled(1);
        report.setAction(action);
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private void hideTarget(String type, Long targetId) {
        if ("post".equals(type)) {
            Post post = postMapper.selectById(targetId);
            if (post != null) {
                // 逻辑删除，详情/信息流/案例库都不再返回
                postMapper.deleteById(targetId);
            }
            return;
        }
        if ("comment".equals(type)) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment != null) {
                comment.setStatus(0);
                commentMapper.updateById(comment);
            }
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
