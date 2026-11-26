package com.lukou.decision.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SolutionVO {

    private Long id;
    private String content;
    private String authorName;
    private String authorAvatar;
    private String authorSceneTag;
    private int authorStars;
    private int likeCount;
    private boolean liked;
    private boolean adopted;
    private boolean mine;
    private LocalDateTime createdAt;

    /** 采纳方案所需的领域标签，由系统按场景生成，不由用户自填 */
    private List<String> availableTags;
}
