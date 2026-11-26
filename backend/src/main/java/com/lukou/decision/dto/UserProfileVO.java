package com.lukou.decision.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileVO {

    private Long id;
    private String nickname;
    private String avatar;
    private String bio;
    private int postCount;
    private int voteCount;
    private int experienceCount;
    private List<ExperienceVO> experiences;
    /** 累计获采纳次数（星星总数） */
    private int stars;
    /** 系统按场景生成的标签 [{scene, stars}] */
    private List<StarTagVO> starTags;
}
