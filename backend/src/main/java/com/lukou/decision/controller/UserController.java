package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.dto.*;
import com.lukou.decision.service.UserService;
import com.lukou.decision.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<UserProfileVO> profile() {
        return R.ok(userService.profile(UserContext.require()));
    }

    @PutMapping
    public R<Void> update(@RequestBody ProfileUpdateReq req) {
        userService.updateProfile(UserContext.require(), req);
        return R.ok();
    }

    @PostMapping("/experiences")
    public R<Map<String, Long>> addExperience(@RequestBody @Valid ExperienceReq req) {
        Long id = userService.addExperience(UserContext.require(), req);
        return R.ok(Map.of("id", id));
    }

    @DeleteMapping("/experiences/{id}")
    public R<Void> deleteExperience(@PathVariable Long id) {
        userService.deleteExperience(UserContext.require(), id);
        return R.ok();
    }
}
