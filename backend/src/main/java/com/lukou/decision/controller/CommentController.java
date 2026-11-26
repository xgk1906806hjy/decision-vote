package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.CommentReq;
import com.lukou.decision.dto.CommentVO;
import com.lukou.decision.service.CommentService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/{id}/comments")
    public R<CommentVO> create(@PathVariable Long id, @RequestBody @Valid CommentReq req) {
        return R.ok(commentService.create(id, req, UserContext.require()));
    }
}
