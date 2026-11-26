package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostCardVO {

    private Long id;
    private String title;
    private String summary;
    private String scene;
    private boolean anonymous;
    private String authorName;
    private String authorAvatar;
    private int voteCount;
    private int commentCount;
    private String status;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private List<OptionVO> options;
    private Long myOptionId;
    private int totalVoteCount;
    /** true=盲投中，票数分布被隐藏，只展示 totalVoteCount 作为参与人数 */
    private boolean blind;
}
