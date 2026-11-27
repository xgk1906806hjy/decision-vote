-- 回访订阅消息提醒：额度表 + 提醒时间落库
-- 上线前在目标库执行一次

ALTER TABLE dv_follow_up
    ADD COLUMN reminded_at DATETIME DEFAULT NULL COMMENT '订阅消息提醒时间，非空不再重复推送' AFTER answered_at;

CREATE TABLE IF NOT EXISTS dv_subscribe (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    tmpl_id    VARCHAR(64) NOT NULL COMMENT '模板别名，当前只有 follow-up',
    remain     INT         NOT NULL DEFAULT 0 COMMENT '剩余可发送次数',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tmpl (user_id, tmpl_id)
) ENGINE = InnoDB COMMENT ='订阅消息额度';
