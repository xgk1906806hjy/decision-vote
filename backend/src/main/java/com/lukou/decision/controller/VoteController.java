package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.PostDetailVO;
import com.lukou.decision.dto.VoteReq;
import com.lukou.decision.service.PostService;
import com.lukou.decision.service.VoteService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;
    private final PostService postService;

    @PostMapping("/{id}/vote")
    public R<PostDetailVO> vote(@PathVariable Long id, @RequestBody @Valid VoteReq req) {
        Long userId = UserContext.require();
        voteService.vote(id, req.getOptionId(), userId);
        return R.ok(postService.detail(id, userId));
    }
}
