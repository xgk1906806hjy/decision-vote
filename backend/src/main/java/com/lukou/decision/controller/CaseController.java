package com.lukou.decision.controller;

import com.lukou.decision.common.PageResult;
import com.lukou.decision.common.R;
import com.lukou.decision.dto.CaseStatsVO;
import com.lukou.decision.dto.CaseVO;
import com.lukou.decision.service.CaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @GetMapping
    public R<PageResult<CaseVO>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String scene,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(caseService.list(Math.max(page, 1), Math.min(size, 50), scene, keyword));
    }

    @GetMapping("/similar")
    public R<List<CaseVO>> similar(@RequestParam(required = false) String scene,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "5") int limit) {
        return R.ok(caseService.similar(scene, keyword, Math.min(limit, 20)));
    }

    @GetMapping("/stats")
    public R<CaseStatsVO> stats(@RequestParam(required = false) String scene) {
        return R.ok(caseService.stats(scene));
    }
}
