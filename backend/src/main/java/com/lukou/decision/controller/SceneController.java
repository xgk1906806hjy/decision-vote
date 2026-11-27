package com.lukou.decision.controller;

import com.lukou.decision.common.R;
import com.lukou.decision.service.SceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景标签。首页、发布页、案例库共用同一份数据源。
 */
@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
public class SceneController {

    private final SceneService sceneService;

    @GetMapping
    public R<List<String>> list() {
        return R.ok(sceneService.list());
    }
}
