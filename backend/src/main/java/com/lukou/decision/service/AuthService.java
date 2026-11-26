package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.dto.LoginReq;
import com.lukou.decision.dto.LoginVO;
import com.lukou.decision.entity.User;
import com.lukou.decision.mapper.UserMapper;
import com.lukou.decision.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String[] NICK_PREFIX = {"路过的", "想清楚的", "犹豫的", "晚睡的", "远行的", "刚做完决定的"};

    private final UserMapper userMapper;
    private final WxApiService wxApiService;
    private final JwtUtil jwtUtil;

    @Transactional
    public LoginVO login(LoginReq req) {
        String openid = wxApiService.code2Openid(req.getCode());

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid).last("limit 1"));

        boolean isNew = false;
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname(req.getNickname() != null && !req.getNickname().isBlank()
                    ? req.getNickname()
                    : NICK_PREFIX[ThreadLocalRandom.current().nextInt(NICK_PREFIX.length)]
                      + ThreadLocalRandom.current().nextInt(100, 999));
            user.setAvatar(req.getAvatar());
            userMapper.insert(user);
            isNew = true;
        } else if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname());
            if (req.getAvatar() != null) {
                user.setAvatar(req.getAvatar());
            }
            userMapper.updateById(user);
        }

        String token = jwtUtil.create(user.getId());
        return new LoginVO(token, user.getId(), user.getNickname(), user.getAvatar(), isNew);
    }
}
