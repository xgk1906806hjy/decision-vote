-- ============================================================
-- 路口（decision_vote）数据库结构
-- MySQL 8.0+，字符集 utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS decision_vote
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE decision_vote;

-- 用户表
CREATE TABLE IF NOT EXISTS dv_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid',
    nickname    VARCHAR(64)  DEFAULT NULL,
    avatar      VARCHAR(512) DEFAULT NULL,
    bio         VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid)
) ENGINE = InnoDB COMMENT ='用户';

-- 经历档案：用户主动声明的「我做过哪些选择、结果如何」
CREATE TABLE IF NOT EXISTS dv_experience (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    scene       VARCHAR(64)  NOT NULL COMMENT '场景，如 考研/辞职/异地恋/买房',
    choice      VARCHAR(128) NOT NULL COMMENT '当时怎么选的',
    years_ago   INT          NOT NULL DEFAULT 0 COMMENT '距今几年',
    outcome     VARCHAR(16)  NOT NULL COMMENT 'worth=值得 regret=后悔 watching=观察中',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_scene (scene)
) ENGINE = InnoDB COMMENT ='经历档案';

-- 决策帖
CREATE TABLE IF NOT EXISTS dv_post (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(120) NOT NULL,
    content       TEXT         NOT NULL,
    scene         VARCHAR(64)  DEFAULT NULL COMMENT '所属场景',
    anonymous     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=匿名展示',
    deadline      DATETIME     DEFAULT NULL COMMENT '投票截止',
    status        VARCHAR(16)  NOT NULL DEFAULT 'open' COMMENT 'open/closed',
    view_count    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_created (status, created_at),
    KEY idx_scene (scene),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='决策帖';

-- 选项
CREATE TABLE IF NOT EXISTS dv_option (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    post_id   BIGINT       NOT NULL,
    label     VARCHAR(120) NOT NULL,
    sort_no   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_post (post_id)
) ENGINE = InnoDB COMMENT ='选项';

-- 投票（一个用户对一个帖只能投一次）
CREATE TABLE IF NOT EXISTS dv_vote (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    post_id    BIGINT   NOT NULL,
    option_id  BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id),
    KEY idx_option (option_id)
) ENGINE = InnoDB COMMENT ='投票记录';

-- 评论 / 过来人建议
CREATE TABLE IF NOT EXISTS dv_comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    option_id   BIGINT       DEFAULT NULL COMMENT '支持哪个选项',
    content     VARCHAR(1000) NOT NULL,
    outcome_tag VARCHAR(16)  DEFAULT NULL COMMENT '给建议的人自己的结果：worth/regret/watching',
    author_type VARCHAR(16)  NOT NULL DEFAULT 'passerby' COMMENT 'experienced=我经历过 passerby=我只是路过',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=隐藏',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_post_created (post_id, created_at),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='评论建议';

-- 决策回访：帖主事后回填结果，形成「决策—结果」数据资产
CREATE TABLE IF NOT EXISTS dv_follow_up (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    post_id        BIGINT      NOT NULL,
    user_id        BIGINT      NOT NULL,
    chose_option_id BIGINT     DEFAULT NULL COMMENT '最终选了哪个',
    outcome        VARCHAR(16) DEFAULT NULL COMMENT 'worth/regret/watching',
    note           VARCHAR(1000) DEFAULT NULL,
    due_at         DATETIME    DEFAULT NULL COMMENT '计划回访时间',
    answered_at    DATETIME    DEFAULT NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post (post_id),
    KEY idx_due (answered_at, due_at)
) ENGINE = InnoDB COMMENT ='决策回访';

-- 举报
CREATE TABLE IF NOT EXISTS dv_report (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(16)  NOT NULL COMMENT 'post/comment',
    target_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    reason      VARCHAR(255) NOT NULL,
    handled     TINYINT      NOT NULL DEFAULT 0,
    action      VARCHAR(16)  DEFAULT NULL COMMENT 'hide=隐藏内容 dismiss=驳回举报',
    handled_at  DATETIME     DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_target (target_type, target_id),
    KEY idx_handled_created (handled, created_at)
) ENGINE = InnoDB COMMENT ='举报';

-- ============================================================
-- 增量：方案 / 采纳 / 星星标签 所需表
-- ============================================================

-- 方案：用户为某个决策提供的第三条路
CREATE TABLE IF NOT EXISTS dv_solution (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    post_id      BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    content      VARCHAR(1000) NOT NULL COMMENT '方案内容',
    adopted      TINYINT      NOT NULL DEFAULT 0 COMMENT '1=被发起者采纳',
    like_count   INT          NOT NULL DEFAULT 0 COMMENT '点赞数，冗余便于排序',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 0=隐藏',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post (post_id, adopted),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='用户提供的方案';

-- 方案点赞：一个用户对一个方案只能赞一次
CREATE TABLE IF NOT EXISTS dv_solution_like (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    solution_id BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_solution_user (solution_id, user_id)
) ENGINE = InnoDB COMMENT ='方案点赞';

-- 用户星星与系统标签：采纳一次得一颗星，并按场景累计标签
CREATE TABLE IF NOT EXISTS dv_user_credit (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    scene       VARCHAR(64) NOT NULL COMMENT '场景，来自被采纳方案所属帖子',
    stars       INT         NOT NULL DEFAULT 0 COMMENT '该场景累计获采纳次数',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_scene (user_id, scene),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT ='用户场景星星';

-- 追加字段：用户总星星数、需要升级脚本（此处给出 DDL 便于手工执行）
-- ALTER TABLE dv_user ADD COLUMN stars INT NOT NULL DEFAULT 0 COMMENT '获采纳总次数';
