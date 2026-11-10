-- ------------------------------------------------------------
-- V003 · 疑点规则新增 CARD_NO_CLASS（买了课包一次没来）
--
-- 背景：v1 的六条规则里，SILENT_BALANCE 依赖 days_since_class，
--       而「从未来过」的会员该字段为空（真实导出里最后上课/距末课天数全空），
--       导致「有 375 课时、到课 0 次」的人一条规则都不命中。
--
-- 本次只改注释，不动结构：
--   reason_code 是 VARCHAR(32)，CARD_NO_CLASS 只有 14 个字符，容量足够。
--
-- spec: cycles/c1-followup/spec.md v0.3 第五节
-- ------------------------------------------------------------

ALTER TABLE followup_flag
  MODIFY COLUMN reason_code VARCHAR(32) NOT NULL
  COMMENT 'NO_MOBILE / MOBILE_BAD / SILENT_BALANCE / STATE_CONFLICT / TEACHER_LEFT / NO_SHOW_199 / CARD_NO_CLASS';
