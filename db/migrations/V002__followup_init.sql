-- ============================================================
-- V002 · 跟进模块（C1）
-- 2026-11-06 · 库 hearth
--
-- 依赖 V001 的 app_shop / app_staff / app_member。
-- 原则：快照表存「当时的原始值」，关联靠 ID；两者并存，展示用快照值，关联用 ID。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 跟进总表快照（每日一份）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_snapshot (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id             BIGINT        NOT NULL,
  snapshot_date       DATE          NOT NULL COMMENT '快照日期',
  import_batch        VARCHAR(32)   NOT NULL COMMENT '导入批次号',

  member_id           BIGINT        NOT NULL COMMENT '★ app_member.id',
  staff_id            BIGINT        NULL     COMMENT '★ app_staff.id（归属老师）；匹配不到为空',

  -- 快照值：当时三体给的样子，不随主数据变化，供展示与追溯
  member_name         VARCHAR(64)   NOT NULL,
  mobile              VARCHAR(32)   NOT NULL COMMENT '全号',
  teacher_name        VARCHAR(32)   NOT NULL COMMENT '老师姓名（快照）',

  tier                VARCHAR(16)   NULL,
  activity            VARCHAR(32)   NULL,
  last_class_date     DATE          NULL,
  days_since_class    INT           NULL,
  attend_count        INT           NULL,
  timecard_expire     DATE          NULL,
  timecard_days_left  INT           NULL,
  countcard_lessons   INT           NULL,
  countcard_days_left INT           NULL,
  last_sign_date      DATE          NULL,
  last_product        VARCHAR(128)  NULL,
  last_sign_amount    DECIMAL(12,2) NULL,
  talk_script         TEXT          NULL,
  total_paid          DECIMAL(12,2) NULL,
  sign_count          INT           NULL,
  bought_199          TINYINT       NULL,
  timecard_kind       VARCHAR(64)   NULL,
  countcard_kind      VARCHAR(64)   NULL,

  queue               VARCHAR(16)   NOT NULL DEFAULT 'NONE'
                      COMMENT 'RED临期 / ORANGE沉默有余额 / YELLOW囤课待消 / BLUE未转化 / NONE',
  sort_key            INT           NOT NULL DEFAULT 0,

  review_status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                      COMMENT 'PENDING / VERIFIED / REJECTED / PARKED',
  review_reason       VARCHAR(64)   NULL COMMENT 'CHANGED / TEACHER_FLAGGED / MANUAL',
  review_note         VARCHAR(255)  NULL,
  reviewed_by         BIGINT        NULL COMMENT '核实人 app_user.id',
  reviewed_at         DATETIME      NULL,
  dispatched_at       DATETIME      NULL COMMENT '首次进入老师视野；状态回退也不清空',

  imported_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_snap_member (snapshot_date, member_id),
  KEY idx_snap_staff (snapshot_date, staff_id),
  KEY idx_snap_review (snapshot_date, review_status),
  KEY idx_snap_queue (snapshot_date, queue, sort_key),
  KEY idx_member (member_id, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进总表日快照 + 核实状态';


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
  member_id    BIGINT       NOT NULL,
  staff_id     BIGINT       NOT NULL COMMENT '执行人 app_user.id',
  action       VARCHAR(16)  NOT NULL
               COMMENT 'CONFIRM / FIX / REJECT / PARK / UNPARK / REOPEN',
  field_name   VARCHAR(32)  NULL,
  old_value    VARCHAR(255) NULL COMMENT '原值（可能含全号，敏感）',
  new_value    VARCHAR(255) NULL,
  note         VARCHAR(500) NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_snap (snapshot_id),
  KEY idx_member (member_id),
  KEY idx_staff_date (staff_id, created_at),
  KEY idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核实动作留痕（敏感）';


-- ------------------------------------------------------------
-- 4. 老师跟进记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id       BIGINT        NOT NULL,
  snapshot_date DATE          NOT NULL,
  member_id     BIGINT        NOT NULL COMMENT '★ app_member.id',
  staff_id      BIGINT        NOT NULL COMMENT '实际记录人 app_user.id',
  follow_date   DATE          NOT NULL,
  result_code   TINYINT       NOT NULL COMMENT '1已续费 2有意向 3考虑中 4没时间 5不续 6未接通 7要顺延 8已流失 9其他',
  result_text   VARCHAR(32)   NULL,
  amount        DECIMAL(12,2) NULL,
  note          VARCHAR(500)  NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_member_date (member_id, follow_date) COMMENT '同一天同一人只留一条，重复提交=覆盖',
  KEY idx_follow_date (follow_date),
  KEY idx_staff_date (staff_id, follow_date),
  KEY idx_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录（小程序回写）';


-- ============================================================
-- 视图
-- ============================================================

CREATE OR REPLACE VIEW v_review_groups AS
SELECT f.reason_code, COUNT(DISTINCT s.id) AS pending_cnt
FROM followup_flag f
JOIN followup_snapshot s ON s.id = f.snapshot_id
WHERE s.snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot)
  AND s.review_status = 'PENDING'
GROUP BY f.reason_code;

CREATE OR REPLACE VIEW v_review_clean AS
SELECT COUNT(*) AS pending_cnt
FROM followup_snapshot s
WHERE s.snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot)
  AND s.review_status = 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM followup_flag f WHERE f.snapshot_id = s.id);

CREATE OR REPLACE VIEW v_today_progress AS
SELECT
  s.staff_id                                    AS staff_id,
  COUNT(*)                                      AS total,
  SUM(CASE WHEN l.id IS NULL THEN 0 ELSE 1 END) AS done
FROM followup_snapshot s
LEFT JOIN followup_log l
       ON l.member_id   = s.member_id
      AND l.follow_date = CURDATE()
WHERE s.snapshot_date = (SELECT MAX(snapshot_date) FROM followup_snapshot)
  AND s.review_status = 'VERIFIED'
  AND s.dispatched_at IS NOT NULL
  AND s.queue <> 'NONE'
GROUP BY s.staff_id;
