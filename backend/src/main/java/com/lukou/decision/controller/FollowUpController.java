package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.FollowUpReq;
import com.lukou.decision.dto.PendingFollowUpVO;
import com.lukou.decision.service.FollowUpService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FollowUpController {

    private final FollowUpService followUpService;

    @PostMapping("/posts/{id}/follow-up")
    public R<Void> submit(@PathVariable Long id, @RequestBody @Valid FollowUpReq req) {
        followUpService.submit(id, req, UserContext.require());
        return R.ok();
    }

    @GetMapping("/follow-ups/pending")
    public R<List<PendingFollowUpVO>> pending() {
        return R.ok(followUpService.pending(UserContext.require()));
    }
}
