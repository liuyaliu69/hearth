-- ============================================================
-- C1 · 今日跟进 · 模块表（库 hearth）
-- v0.2 · 2026-11-06
--
-- 平台表（app_user / app_role 等）由 C0 的 V001 创建，这里只管 followup 模块。
-- 部署：ECS 上与「小智」共用 MySQL 实例，但独立库 `hearth` + 独立账号。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 跟进总表快照（每日一份）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_snapshot (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  snapshot_date       DATE          NOT NULL COMMENT '快照日期',
  import_batch        VARCHAR(32)   NOT NULL COMMENT '导入批次号',
  teacher             VARCHAR(32)   NOT NULL COMMENT '分配老师',
  tier                VARCHAR(16)   NULL COMMENT '分层',
  member_name         VARCHAR(64)   NOT NULL COMMENT '学员',
  mobile              VARCHAR(32)   NOT NULL COMMENT '手机（全号，仅服务端使用，永不下发）',
  activity            VARCHAR(32)   NULL COMMENT '活跃度',
  last_class_date     DATE          NULL COMMENT '最后上课',
  days_since_class    INT           NULL COMMENT '距末课天数',
  attend_count        INT           NULL COMMENT '到课次数',
  timecard_expire     DATE          NULL COMMENT '时间卡_到期日',
  timecard_days_left  INT           NULL COMMENT '时间卡_剩余天数',
  countcard_lessons   INT           NULL COMMENT '次卡_剩余课时',
  countcard_days_left INT           NULL COMMENT '次卡_剩余天数',
  last_sign_date      DATE          NULL COMMENT '最近签单',
  last_product        VARCHAR(128)  NULL COMMENT '最近买的产品',
  last_sign_amount    DECIMAL(12,2) NULL COMMENT '最近签单金额',
  talk_script         TEXT          NULL COMMENT '话术（原样保留）',
  total_paid          DECIMAL(12,2) NULL COMMENT '累计实收',
  sign_count          INT           NULL COMMENT '签单笔数',
  bought_199          TINYINT       NULL COMMENT '买过199 1=是',
  timecard_kind       VARCHAR(64)   NULL COMMENT '时间卡_卡种',
  countcard_kind      VARCHAR(64)   NULL COMMENT '次卡_卡种',

  queue               VARCHAR(16)   NOT NULL DEFAULT 'NONE'
                      COMMENT 'RED临期 / ORANGE沉默有余额 / YELLOW囤课待消 / BLUE未转化 / NONE',
  sort_key            INT           NOT NULL DEFAULT 0
                      COMMENT '排序键，越小越靠前；临期优先',

  review_status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                      COMMENT 'PENDING / VERIFIED / REJECTED / PARKED',
  review_reason       VARCHAR(64)   NULL COMMENT 'CHANGED / TEACHER_FLAGGED / MANUAL',
  review_note         VARCHAR(255)  NULL,
  reviewed_by         BIGINT        NULL COMMENT '核实人 app_user.id',
  reviewed_at         DATETIME      NULL,
  dispatched_at       DATETIME      NULL COMMENT '首次进入老师视野的时间',

  imported_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_snap_mobile (snapshot_date, mobile),
  KEY idx_snap_teacher (snapshot_date, teacher),
  KEY idx_snap_review (snapshot_date, review_status),
  KEY idx_snap_queue (snapshot_date, queue, sort_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进总表日快照 + 核实状态';

-- 继承规则由导入脚本实现：
--   上次 VERIFIED + 4 个关键字段未变 → 继承 VERIFIED
--   关键字段：mobile / countcard_lessons / timecard_expire / teacher
--   任一变化 → PENDING + review_reason='CHANGED'
--   dispatched_at 从上一期继承，不因退回 PENDING 清空


-- ------------------------------------------------------------
-- 2. 疑点标记
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_flag (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  snapshot_id BIGINT      NOT NULL,
  reason_code VARCHAR(32) NOT NULL
              COMMENT 'MOBILE_BAD / SILENT_BALANCE / STATE_CONFLICT / TEACHER_LEFT / NO_SHOW_199 / NO_MOBILE',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_snap_reason (snapshot_id, reason_code),
  KEY idx_reason (reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快照命中的疑点规则';


-- ------------------------------------------------------------
-- 3. 核实留痕（含原值，只允许 owner 写，不提供查询接口）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_review_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  snapshot_id  BIGINT       NOT NULL,
  staff_id     BIGINT       NOT NULL COMMENT '执行人 app_user.id',
  action       VARCHAR(16)  NOT NULL
               COMMENT 'CONFIRM / FIX / REJECT / PARK / UNPARK / REOPEN',
  field_name   VARCHAR(32)  NULL,
  old_value    VARCHAR(255) NULL COMMENT '原值（可能含全号，敏感）',
  new_value    VARCHAR(255) NULL,
  note         VARCHAR(500) NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_snap (snapshot_id),
  KEY idx_staff_date (staff_id, created_at),
  KEY idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核实动作留痕（敏感）';


-- ------------------------------------------------------------
-- 4. 老师跟进记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  snapshot_date DATE          NOT NULL,
  member_name   VARCHAR(64)   NULL,
  mobile        VARCHAR(32)   NOT NULL,
  teacher       VARCHAR(32)   NULL,
  staff_id      BIGINT        NOT NULL COMMENT '实际记录人',
  follow_date   DATE          NOT NULL,
  result_code   TINYINT       NOT NULL COMMENT '1已续费 2有意向 3考虑中 4没时间 5不续 6未接通 7要顺延 8已流失 9其他',
  result_text   VARCHAR(32)   NULL,
  amount        DECIMAL(12,2) NULL,
  note          VARCHAR(500)  NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mobile_date (mobile, follow_date) COMMENT '同一天同一人只留一条',
  KEY idx_follow_date (follow_date),
  KEY idx_staff_date (staff_id, follow_date),
  KEY idx_teacher_date (teacher, follow_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录（小程序回写）';


-- ============================================================
-- 视图
-- ============================================================

-- ① 核实工作台：按疑点规则分组统计
CREATE OR REPLACE VIEW v_review_groups AS
SELECT f.reason_code, COUNT(DISTINCT s.id) AS pending_cnt
FROM followup_flag f
JOIN followup_snapshot s ON s.id = f.snapshot_id
WHERE s.snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot)
  AND s.review_status = 'PENDING'
GROUP BY f.reason_code;

-- ② 无疑点待批量确认
CREATE OR REPLACE VIEW v_review_clean AS
SELECT COUNT(*) AS pending_cnt
FROM followup_snapshot s
WHERE s.snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot)
  AND s.review_status = 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM followup_flag f WHERE f.snapshot_id = s.id);

-- ③ 今日进度（分母只算已核实且已分发）
CREATE OR REPLACE VIEW v_today_progress AS
SELECT
  s.teacher                                     AS teacher,
  COUNT(*)                                      AS total,
  SUM(CASE WHEN l.id IS NULL THEN 0 ELSE 1 END) AS done
FROM followup_snapshot s
LEFT JOIN followup_log l
       ON l.mobile        = s.mobile
      AND l.follow_date   = CURDATE()
      AND l.snapshot_date = s.snapshot_date
WHERE s.snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot)
  AND s.review_status = 'VERIFIED'
  AND s.dispatched_at IS NOT NULL
  AND s.queue <> 'NONE'
GROUP BY s.teacher;


-- ============================================================
-- 导入后自检（校准用）
-- ============================================================
-- 疑点命中率（期望 10–30%）
-- SELECT (SELECT COUNT(DISTINCT snapshot_id) FROM followup_flag) / COUNT(*) FROM followup_snapshot
--  WHERE snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot);
-- 各规则命中：  SELECT * FROM v_review_groups ORDER BY pending_cnt DESC;
-- 无疑点数：    SELECT * FROM v_review_clean;
-- 队列分布：    SELECT queue, COUNT(*) FROM followup_snapshot
--                WHERE snapshot_date=(SELECT MAX(snapshot_date) FROM followup_snapshot)
--                GROUP BY queue;
-- 手机号完整性（应为 0）：
-- SELECT COUNT(*) FROM followup_snapshot WHERE mobile NOT REGEXP '^1[0-9]{10}$';
