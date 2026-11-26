package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.ReportReq;
import com.lukou.decision.service.ReportService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public R<Void> create(@RequestBody @Valid ReportReq req) {
        reportService.create(req, UserContext.require());
        return R.ok();
    }
}
