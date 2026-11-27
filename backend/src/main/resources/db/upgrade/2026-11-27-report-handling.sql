-- 举报处置后台：记录处理动作与处理时间
-- 适用于已执行过旧版 schema.sql 的数据库
ALTER TABLE dv_report
    ADD COLUMN action VARCHAR(16) DEFAULT NULL COMMENT 'hide=隐藏内容 dismiss=驳回举报' AFTER handled,
    ADD COLUMN handled_at DATETIME DEFAULT NULL AFTER action,
    ADD KEY idx_handled_created (handled, created_at);
