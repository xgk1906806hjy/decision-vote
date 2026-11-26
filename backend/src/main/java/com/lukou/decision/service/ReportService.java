package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.dto.ReportReq;
import com.lukou.decision.entity.Comment;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.Report;
import com.lukou.decision.mapper.CommentMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * 举报。UGC 产品的内容安全兜底：先由用户发现，落库后人工处理。
 * 处理入口在后台，当前只负责收单与去重。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Set<String> TYPES = Set.of("post", "comment");

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    @Transactional
    public void create(ReportReq req, Long userId) {
        String type = req.getTargetType() == null ? "" : req.getTargetType().trim().toLowerCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException(400, "不支持的举报类型");
        }

        Long ownerId = resolveOwner(type, req.getTargetId());
        if (Objects.equals(ownerId, userId)) {
            throw new BusinessException(400, "不能举报自己发布的内容");
        }

        // 同一个人对同一个内容只记一次，避免刷量干扰处理排序
        Long dup = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, type)
                .eq(Report::getTargetId, req.getTargetId())
                .eq(Report::getUserId, userId));
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "你已经举报过了，我们会尽快处理");
        }

        Report report = new Report();
        report.setTargetType(type);
        report.setTargetId(req.getTargetId());
        report.setUserId(userId);
        report.setReason(req.getReason().trim());
        report.setHandled(0);
        reportMapper.insert(report);
    }

    /** 返回被举报内容的作者，用于「不能举报自己」的校验 */
    private Long resolveOwner(String type, Long targetId) {
        if ("post".equals(type)) {
            Post post = postMapper.selectById(targetId);
            if (post == null) {
                throw new BusinessException(404, "被举报的决策不存在");
            }
            return post.getUserId();
        }
        Comment comment = commentMapper.selectById(targetId);
        if (comment == null) {
            throw new BusinessException(404, "被举报的建议不存在");
        }
        return comment.getUserId();
    }
}
