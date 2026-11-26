package com.lukou.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lukou.decision.common.BusinessException;
import com.lukou.decision.dto.ExperienceReq;
import com.lukou.decision.dto.ExperienceVO;
import com.lukou.decision.dto.ProfileUpdateReq;
import com.lukou.decision.dto.StarTagVO;
import com.lukou.decision.dto.UserProfileVO;
import com.lukou.decision.entity.Experience;
import com.lukou.decision.entity.Post;
import com.lukou.decision.entity.User;
import com.lukou.decision.entity.UserCredit;
import com.lukou.decision.entity.Vote;
import com.lukou.decision.mapper.ExperienceMapper;
import com.lukou.decision.mapper.PostMapper;
import com.lukou.decision.mapper.UserCreditMapper;
import com.lukou.decision.mapper.UserMapper;
import com.lukou.decision.mapper.VoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> TAGS = Set.of("worth", "regret", "watching");

    private final UserMapper userMapper;
    private final ExperienceMapper experienceMapper;
    private final PostMapper postMapper;
    private final VoteMapper voteMapper;
    private final UserCreditMapper creditMapper;
    private final ContentAuditService audit;

    public UserProfileVO profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setPostCount(Math.toIntExact(postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getUserId, userId))));
        vo.setVoteCount(Math.toIntExact(voteMapper.selectCount(
                new LambdaQueryWrapper<Vote>().eq(Vote::getUserId, userId))));

        List<Experience> exp = experienceMapper.selectList(new LambdaQueryWrapper<Experience>()
                .eq(Experience::getUserId, userId).orderByDesc(Experience::getCreatedAt));
        vo.setExperienceCount(exp.size());
        vo.setExperiences(exp.stream().map(e -> {
            ExperienceVO ev = new ExperienceVO();
            ev.setId(e.getId());
            ev.setScene(e.getScene());
            ev.setChoice(e.getChoice());
            ev.setYearsAgo(e.getYearsAgo());
            ev.setOutcome(e.getOutcome());
            return ev;
        }).collect(Collectors.toList()));

        List<UserCredit> credits = creditMapper.selectList(new LambdaQueryWrapper<UserCredit>()
                .eq(UserCredit::getUserId, userId)
                .orderByDesc(UserCredit::getStars));
        int totalStars = credits.stream().mapToInt(c -> c.getStars() == null ? 0 : c.getStars()).sum();
        vo.setStars(totalStars);
        vo.setStarTags(credits.stream().map(c -> {
            StarTagVO t = new StarTagVO();
            t.setScene(c.getScene());
            t.setStars(c.getStars() == null ? 0 : c.getStars());
            return t;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Transactional
    public void updateProfile(Long userId, ProfileUpdateReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            audit.check(req.getNickname());
            if (req.getNickname().length() > 32) {
                throw new BusinessException("昵称最多 32 字");
            }
            user.setNickname(req.getNickname().trim());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }
        if (req.getBio() != null) {
            audit.check(req.getBio());
            user.setBio(req.getBio());
        }
        userMapper.updateById(user);
    }

    @Transactional
    public Long addExperience(Long userId, ExperienceReq req) {
        if (req.getOutcome() != null && !TAGS.contains(req.getOutcome())) {
            throw new BusinessException("结果标签不合法");
        }
        audit.check(req.getScene());
        audit.check(req.getChoice());

        Experience e = new Experience();
        e.setUserId(userId);
        e.setScene(req.getScene().trim());
        e.setChoice(req.getChoice().trim());
        e.setYearsAgo(req.getYearsAgo() == null ? 0 : req.getYearsAgo());
        e.setOutcome(req.getOutcome());
        experienceMapper.insert(e);
        return e.getId();
    }

    @Transactional
    public void deleteExperience(Long userId, Long id) {
        Experience e = experienceMapper.selectById(id);
        if (e == null || !Objects.equals(e.getUserId(), userId)) {
            throw new BusinessException(404, "记录不存在");
        }
        experienceMapper.deleteById(id);
    }
}
