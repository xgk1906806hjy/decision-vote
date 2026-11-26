package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.SolutionReq;
import com.lukou.decision.dto.SolutionVO;
import com.lukou.decision.service.SolutionService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @PostMapping("/api/posts/{id}/solutions")
    public R<Map<String, Long>> create(@PathVariable Long id,
                                       @RequestBody @Valid SolutionReq req) {
        Long solutionId = solutionService.create(id, req, UserContext.require());
        return R.ok(Map.of("id", solutionId));
    }

    @GetMapping("/api/posts/{id}/solutions")
    public R<List<SolutionVO>> list(@PathVariable Long id) {
        return R.ok(solutionService.list(id, UserContext.get()));
    }

    @PostMapping("/api/solutions/{id}/like")
    public R<Void> like(@PathVariable Long id) {
        solutionService.like(id, UserContext.require());
        return R.ok();
    }

    @PostMapping("/api/posts/{id}/adopt")
    public R<Void> adopt(@PathVariable Long id, @RequestParam Long solutionId) {
        solutionService.adopt(id, solutionId, UserContext.require());
        return R.ok();
    }
}
