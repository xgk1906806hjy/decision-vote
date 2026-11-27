-- 建议作者身份：我经历过 / 我只是路过
-- 适用于已执行过旧版 schema.sql 的数据库
ALTER TABLE dv_comment
    ADD COLUMN author_type VARCHAR(16) NOT NULL DEFAULT 'passerby'
        COMMENT 'experienced=我经历过 passerby=我只是路过' AFTER outcome_tag;
