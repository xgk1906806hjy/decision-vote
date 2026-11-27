package com.lukou.decision.controller;

import com.lukou.decision.common.BusinessException;
import com.lukou.decision.common.PageResult;
import com.lukou.decision.common.R;
import com.lukou.decision.config.AdminProperties;
import com.lukou.decision.dto.HandleReportReq;
import com.lukou.decision.dto.ReportVO;
import com.lukou.decision.service.ReportAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportAdminService reportAdminService;
    private final AdminProperties adminProperties;

    @GetMapping
    public R<PageResult<ReportVO>> list(
            @RequestParam(defaultValue = "0") int handled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        checkToken(token);
        return R.ok(reportAdminService.list(handled, page, size));
    }

    @PostMapping("/{id}/handle")
    public R<Void> handle(
            @PathVariable Long id,
            @RequestBody @Valid HandleReportReq req,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        checkToken(token);
        reportAdminService.handle(id, req.getAction());
        return R.ok();
    }

    private void checkToken(String token) {
        String expected = adminProperties.getToken();
        if (expected == null || expected.isBlank() || token == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "管理员鉴权失败");
        }
    }
}
