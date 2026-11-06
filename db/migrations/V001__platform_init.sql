-- ============================================================
-- V001 · 平台主数据
-- 2026-11-06 · 库 hearth
--
-- 建的是「谁、在哪」——变化慢、影响面大的那一层。
-- 过程数据（订单/卡/预约/课消）在三体，我们只镜像不拥有。
--
-- 六条结构原则：
--   1. 代理主键 + 外部引用（source_system + external_id）
--   2. 主数据只增不删，用 status 表达，用 merged_into 表达合并
--   3. 所属关系留当前值；历史靠快照与流水
--   4. 余额是派生值，不存字段
--   5. 业务时间与系统时间分开
--   6. 所有业务表带 shop_id（现在 1 家店，字段先留）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 门店
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_shop (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(32)  NOT NULL COMMENT '短码，如 fire-buji',
  name        VARCHAR(64)  NOT NULL,
  address     VARCHAR(255) NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / CLOSED',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店';

INSERT INTO app_shop (code, name, address)
SELECT 'fire-buji', 'FIRE舞蹈工作室（布吉店）', '深圳布吉'
WHERE NOT EXISTS (SELECT 1 FROM app_shop WHERE code = 'fire-buji');


-- ------------------------------------------------------------
-- 2. 员工（店长 / 前台 / 老师，都是员工）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_staff (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id         BIGINT       NOT NULL,
  name            VARCHAR(32)  NOT NULL COMMENT '姓名；导入时用它匹配，改名要同步',
  mobile          VARCHAR(32)  NULL,
  job             VARCHAR(16)  NOT NULL DEFAULT 'teacher'
                  COMMENT 'owner 老板 / manager 店长 / front 前台 / teacher 老师 / other',
  employment      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                  COMMENT 'ACTIVE 在职 / LEFT 离职',
  hired_on        DATE         NULL,
  left_on         DATE         NULL,
  commission_rate DECIMAL(5,4) NULL COMMENT '★ 按成交额计提的提成比例（0.0500 = 5%）',
  wechat          VARCHAR(64)  NULL,
  note            VARCHAR(255) NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_shop (shop_id),
  KEY idx_name (name),
  KEY idx_employment (employment),
  KEY idx_job (job)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工（含老师）';

-- 姓名不加唯一键：同店重名存在（且改名是常态）
-- 离职用 employment=LEFT，不删行——历史记录要能指向它


-- ------------------------------------------------------------
-- 3. 账号与角色
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id       BIGINT       NOT NULL,
  staff_id      BIGINT       NULL COMMENT '关联员工；为空表示外部协作账号',
  openid        VARCHAR(64)  NULL COMMENT '微信 openid',
  name          VARCHAR(32)  NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  last_login_at DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_openid (openid),
  KEY idx_shop (shop_id),
  KEY idx_staff (staff_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录账号（与员工分离）';

CREATE TABLE IF NOT EXISTS app_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(32)  NOT NULL COMMENT 'owner / teacher / operator',
  name        VARCHAR(32)  NOT NULL,
  description VARCHAR(255) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

INSERT INTO app_role (code, name, description) VALUES
  ('owner',    '老板', '全部数据 + 核实 + 账号管理'),
  ('teacher',  '老师', '仅自己名下、已核实的学员'),
  ('operator', '运营', '预留：活动、内容、触达')
ON DUPLICATE KEY UPDATE name = VALUES(name);

CREATE TABLE IF NOT EXISTS app_user_role (
  user_id    BIGINT   NOT NULL,
  role_id    BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色（多对多）';

-- 权限点不建表：角色 → 权限的映射写死在 SecurityConfig（3 个角色不值得做通用 RBAC）
-- 数据范围用角色决定：owner=ALL，teacher=SELF


-- ------------------------------------------------------------
-- 4. 学员（平台核心主数据）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_member (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '★ 我们生成的会员 ID',
  shop_id         BIGINT       NOT NULL COMMENT '不跨店，直接归属',
  name            VARCHAR(64)  NOT NULL,
  mobile          VARCHAR(32)  NULL COMMENT '全号，出口需脱敏',
  gender          VARCHAR(8)   NULL,
  birthday        VARCHAR(16)  NULL,

  owner_staff_id  BIGINT       NULL COMMENT '当前归属老师/销售（历史变更靠快照追溯）',
  source_channel  VARCHAR(32)  NULL COMMENT '首次来源：美团 / 抖音 / 大众点评 / 老带新 / 线下 / 未知',
  source_system   VARCHAR(32)  NULL COMMENT '数据来自哪个系统：santi / manual',
  external_id     VARCHAR(64)  NULL COMMENT '外部系统里的会员编号',

  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                  COMMENT 'ACTIVE 正常 / LEFT 已流失 / MERGED 已合并',
  merged_into     BIGINT       NULL COMMENT '合并到哪个 member_id',
  first_seen      DATE         NULL COMMENT '首次出现日期',

  note            VARCHAR(255) NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  UNIQUE KEY uk_mobile_name (mobile, name)
    COMMENT '同手机同姓名只建一次，保证重复导入幂等；允许一号多人',
  KEY idx_shop (shop_id),
  KEY idx_name (name),
  KEY idx_owner (owner_staff_id),
  KEY idx_status (status),
  KEY idx_external (source_system, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学员（主数据）';

-- 为什么手机号不单独唯一：家庭共用号很常见，一个号可能对应母女两人
-- 为什么保留 external_id：三体有会员编号，将来可对齐；但主键永远是我们自己的 id
