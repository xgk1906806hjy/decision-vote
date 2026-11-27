package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.entity.Post;
import com.lukou.decision.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 场景标签：8 个预设 + 用户自定义（按使用量排序取前几个）。
 * 自定义标签不做白名单，只做长度与内容安全校验，避免"考公/体制内"这类真实场景被挡在外面。
 */
@Service
@RequiredArgsConstructor
public class SceneService {

    /** 预设场景，顺序即前端 chip 顺序 */
    public static final List<String> PRESET =
            List.of("职场", "感情", "考研", "留学", "买房", "创业", "家庭", "其他");

    /** 自定义场景最多追加几个，避免 chip 行过长 */
    private static final int MAX_CUSTOM = 6;

    /** 自定义场景最大字数 */
    public static final int MAX_LEN = 10;

    private final PostMapper postMapper;

    /**
     * 预设场景 + 站内已被使用的自定义场景（按帖子数倒序）。
     * 单条 SQL 聚合，帖子量级变大也只是扫一遍 scene 索引。
     */
    public List<String> list() {
        List<String> result = new ArrayList<>(PRESET);
        List<Map<String, Object>> rows = postMapper.selectMaps(new QueryWrapper<Post>()
                .select("scene", "COUNT(*) AS cnt")
                .isNotNull("scene")
                .ne("scene", "")
                .groupBy("scene")
                .orderByDesc("cnt")
                .last("LIMIT 40"));
        for (Map<String, Object> row : rows) {
            String scene = Objects.toString(row.get("scene"), "").trim();
            if (scene.isEmpty() || result.contains(scene)) {
                continue;
            }
            result.add(scene);
            if (result.size() >= PRESET.size() + MAX_CUSTOM) {
                break;
            }
        }
        return result;
    }

    /**
     * 归一化并校验场景标签，返回 trim 后的值。
     */
    public String normalize(String raw) {
        String scene = raw == null ? "" : raw.trim();
        if (scene.isEmpty()) {
            throw new BusinessException("请选择所属场景");
        }
        if (scene.length() > MAX_LEN) {
            throw new BusinessException("场景标签最多 " + MAX_LEN + " 字");
        }
        return scene;
    }
}
