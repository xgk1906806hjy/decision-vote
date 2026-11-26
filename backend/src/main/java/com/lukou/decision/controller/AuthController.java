package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.LoginReq;
import com.lukou.decision.dto.LoginVO;
import com.lukou.decision.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public R<LoginVO> login(@RequestBody @Valid LoginReq req) {
        return R.ok(authService.login(req));
    }

    @GetMapping("/health")
    public R<Map<String, String>> health() {
        return R.ok(Map.of("status", "up"));
    }
}
