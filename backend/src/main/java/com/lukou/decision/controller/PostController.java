package com.lukou.decision.controller;

import com.lukou.decision.common.PageResult;
import com.lukou.decision.common.R;
import com.lukou.decision.dto.CreatePostReq;
import com.lukou.decision.dto.PostCardVO;
import com.lukou.decision.dto.PostDetailVO;
import com.lukou.decision.service.PostService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public R<Map<String, Long>> create(@RequestBody @Valid CreatePostReq req) {
        Long id = postService.create(req, UserContext.require());
        return R.ok(Map.of("id", id));
    }

    @GetMapping("/feed")
    public R<PageResult<PostCardVO>> feed(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(required = false) String scene) {
        Long userId = UserContext.get();
        return R.ok(postService.feed(Math.max(page, 1), Math.min(size, 50), scene, userId));
    }

    @GetMapping("/{id}")
    public R<PostDetailVO> detail(@PathVariable Long id) {
        return R.ok(postService.detail(id, UserContext.get()));
    }

    @GetMapping("/mine")
    public R<PageResult<PostCardVO>> mine(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return R.ok(postService.myPosts(UserContext.require(), page, size));
    }

    @GetMapping("/voted")
    public R<PageResult<PostCardVO>> voted(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return R.ok(postService.myVoted(UserContext.require(), page, size));
    }
}
