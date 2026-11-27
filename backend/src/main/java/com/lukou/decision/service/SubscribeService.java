package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.config.SubscribeProperties;
import com.lukou.decision.dto.SubscribeStatusVO;
import com.lukou.decision.entity.Subscribe;
import com.lukou.decision.mapper.SubscribeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 一次性订阅额度管理。额度是"发送许可证"，不是业务次数：
 * 用户每授权一次 +1，每成功发送一条 -1。
 */
@Service
@RequiredArgsConstructor
public class SubscribeService {

    /** 当前只有回访提醒一类模板；将来加模板时这里扩成常量表 */
    public static final String TMPL_FOLLOW_UP = "follow-up";

    private final SubscribeMapper subscribeMapper;
    private final SubscribeProperties props;

    public SubscribeStatusVO status(Long userId) {
        Subscribe sub = find(userId);
        SubscribeStatusVO vo = new SubscribeStatusVO();
        vo.setRemain(sub == null || sub.getRemain() == null ? 0 : sub.getRemain());
        vo.setMaxRemain(props.getMaxRemain());
        vo.setTemplateReady(isTemplateReady());
        return vo;
    }

    /**
     * 用户授权成功后调用。超过上限不再累加，避免一次授权被反复"囤"。
     */
    public SubscribeStatusVO grant(Long userId) {
        Subscribe sub = find(userId);
        int remain = sub == null || sub.getRemain() == null ? 1 : sub.getRemain() + 1;
        remain = Math.min(remain, Math.max(1, props.getMaxRemain()));
        if (sub == null) {
            sub = new Subscribe();
            sub.setUserId(userId);
            sub.setTmplId(TMPL_FOLLOW_UP);
            sub.setRemain(remain);
            subscribeMapper.insert(sub);
        } else {
            sub.setRemain(remain);
            sub.setUpdatedAt(LocalDateTime.now());
            subscribeMapper.updateById(sub);
        }
        return status(userId);
    }

    /** 消耗一次额度，返回是否消耗成功 */
    public boolean consume(Long userId) {
        Subscribe sub = find(userId);
        if (sub == null || sub.getRemain() == null || sub.getRemain() <= 0) {
            return false;
        }
        sub.setRemain(sub.getRemain() - 1);
        sub.setUpdatedAt(LocalDateTime.now());
        subscribeMapper.updateById(sub);
        return true;
    }

    /** 额度作废（用户拒收等场景），避免后续继续无效发送 */
    public void clear(Long userId) {
        Subscribe sub = find(userId);
        if (sub == null || sub.getRemain() == null || sub.getRemain() == 0) {
            return;
        }
        sub.setRemain(0);
        sub.setUpdatedAt(LocalDateTime.now());
        subscribeMapper.updateById(sub);
    }

    public int remain(Long userId) {
        Subscribe sub = find(userId);
        return sub == null || sub.getRemain() == null ? 0 : sub.getRemain();
    }

    public boolean isTemplateReady() {
        String id = props.getFollowUpTemplateId();
        return id != null && !id.isBlank();
    }

    private Subscribe find(Long userId) {
        return subscribeMapper.selectOne(new LambdaQueryWrapper<Subscribe>()
                .eq(Subscribe::getUserId, userId)
                .eq(Subscribe::getTmplId, TMPL_FOLLOW_UP)
                .last("limit 1"));
    }
}
