# C1 · 设计说明书

> v1.1 · 2026-11-09 · 待评审
> 依据：`spec.md` v0.2 · `api.md` v0.2 · `wireframe.md` v0.1 · `db/migrations/V002__followup_init.sql`
> v1.1 变更：文档依据从已删除的 `db/cloud.sql` 改为正式迁移文件；鉴权改用 C0 的 `DataScopeService`
> 本文回答「**怎么实现**」，不是「做什么」。

---

## 一、系统架构

```
┌─────────────┐   HTTPS    ┌──────────────┐  proxy_pass  ┌─────────────────┐
│  小程序      │ ─────────→ │ nginx :443   │ ───────────→ │ Spring Boot     │
│ (3 个体验成员)│            │ /hearth/     │ 127.0.0.1:8080│ JDK 21          │
└─────────────┘             └──────────────┘              └────────┬────────┘
                                                                     │ JDBC
                                                           ┌─────────▼────────┐
                                                           │ MySQL 8.0.44     │
                                                           │ database: hearth │
                                                           └─────────▲────────┘
                                                                     │ 每日导入
                                              ┌──────────────────────┴──────┐
                                              │ 导入模式（同一个 jar）        │
                                              │ java -jar app.jar --import=x │
                                              └──────────────▲──────────────┘
                                                             │ 上传 CSV
                                              ┌──────────────┴──────────────┐
                                              │ 本地 fireDance 导出跟进总表   │
                                              └─────────────────────────────┘
```

**关键点：导入不是一个独立脚本，是同一个 jar 的另一种启动模式。**
理由：疑点规则和继承逻辑只能有一份实现。如果导入用 Python、线上用 Java，两边规则迟早分叉。

---

## 二、平台定位（补充）

这个服务不是「跟进工具」，是「火花私域」运营平台。边界要预留，但不要预先抽象。

| 要预留（现在几乎零成本） | 不预做（过度设计） |
| --- | --- |
| 包结构按模块分：`common` / `auth` / `followup` | 不拆微服务 |
| 接口路径带模块前缀：`/api/followup/**` | 不做多租户 |
| 数据库命名中性，平台表 `app_` 前缀 | 不做通用 RBAC 权限表（角色写死在配置里） |
| 鉴权用 Spring Security | 不为不存在的模块写任何接口或表 |

---

## 三、技术选型（含拒绝的方案）

| 项 | 选择 | 为什么不选另一个 |
| --- | --- | --- |
| 语言 | Java 21 | 服务器只有 JDK 21；本机 JDK 24，**编译必须 `release=21`** |
| 框架 | Spring Boot 3.x | 你的主场 |
| 持久层 | **MyBatis** | 现有库有 9 个视图、大量 SQL 口径；JPA 的 JPQL 反而绕 |
| 鉴权 | **Spring Security 6 + JWT** | 平台第一模块，后续会并入更多模块与角色 |
| 连接池 | HikariCP | `maximumPoolSize=5` |
| 校验 | `jakarta.validation` | 错误信息统一 |
| 日志 | SLF4J + Logback → journald | 不落文件 |
| 前端 | 原生小程序 + **Vant Weapp** | 不上 Taro/uni-app |

---

## 四、模块划分与目录结构

```
backend/
├── pom.xml
└── src/main/
    ├── java/cn/firedance/hearth/
    │   ├── HearthApplication.java        # 启动入口，兼导入模式入口
    │   ├── config/
    │   │   ├── SecurityConfig.java       # SecurityFilterChain：CORS/CSRF/Session 全显式关闭
    │   │   └── AppProperties.java        # appid/secret/jwt 密钥等
    │   ├── common/                       # 平台级公共
    │   │   ├── ApiResponse.java          # {code,msg,data}
    │   │   ├── ErrorCode.java
    │   │   ├── BizException.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   └── MaskUtil.java             # 手机号脱敏，唯一实现
    │   ├── auth/                         # 平台级：登录与鉴权
    │   │   ├── AuthController.java
    │   │   ├── WechatService.java        # code2Session
    │   │   ├── JwtUtil.java
    │   │   ├── JwtAuthenticationFilter.java
    │   │   ├── RestAuthEntryPoint.java   # 401 JSON
    │   │   ├── RestAccessDeniedHandler.java # 403 JSON
    │   │   └── StaffContext.java
    │   └── followup/                     # ← 模块一：跟进
    │       ├── web/
    │       │   ├── ReviewController.java
    │       │   ├── TaskController.java
    │       │   ├── MemberController.java
    │       │   ├── FollowupController.java
    │       │   └── StatsController.java
    │       ├── service/
    │       ├── mapper/
    │       ├── domain/
    │       └── importer/
    │           ├── CsvImporter.java
    │           └── SuspectRuleEngine.java
    └── resources/
        ├── application.yml
        ├── application-prod.yml
        └── mapper/*.xml
```

**分层铁律**：Controller 不写业务、Mapper 不写业务，业务只在 Service。

---

## 五、关键流程设计

### 5.1 登录鉴权

```
小程序          后端                      微信服务器
  │ wx.login     │                            │
  │──code───────→│                            │
  │              │──code + appid + secret────→│
  │              │←─openid────────────────────│
  │              │ 查 app_user（C0 建的表）    │
  │              │ 无此人 → 403               │
  │              │ 有 → 签 JWT(7天)           │
  │←─token+user──│                            │
```

- `appid` / `secret` 从环境变量读
- JWT 载荷只放 `userId`、`role`
- 除 `/api/auth/login` 和 `/api/health`，全部走 Security filter chain
- 不做 refresh token

### 5.2 导入（命令行模式）

```
java -jar app.jar --spring.profiles.active=import --import=/tmp/followup.csv
```

七步，一个事务：

1. 解析 CSV（按列名映射，缺列记 null 不报错）
2. 计算 `queue` / `sort_key`（临期优先）
3. 跑疑点规则 → 生成标记集合
4. 查上一期同手机号记录，判断继承
5. 批量 upsert `followup_snapshot`
6. 批量写 `followup_flag`
7. 打印统计：总行数 / 各疑点命中数 / 继承数 / 待核数

**失败即整体回滚**。重复导入同一天会覆盖，靠 `(snapshot_date, mobile)` 唯一键。

### 5.3 核实 → 分发 → 跟进

状态流转严格由 Service 控制，**不允许 Controller 直接改 `review_status`**：

| 动作 | 前置校验 | 副作用 |
| --- | --- | --- |
| 确认 | 当前 PENDING | 写 review_log |
| 修正 | 当前 PENDING，字段在白名单内 | 写 review_log（含原值）、更新字段 |
| 排除 | 当前 PENDING | 写 review_log |
| 挂起 | 当前 PENDING | 写 review_log |
| 老师退回 | 当前 VERIFIED 且已分发 | 置 PENDING + `review_reason=TEACHER_FLAGGED` |

**「已分发」定义**：`dispatched_at IS NOT NULL`，老师第一次被查到这条时写入（懒分发）。

### 5.4 核实结果继承

```
prev = 上一期同 mobile 的记录
if prev is null:
    status = PENDING
elif prev.status == VERIFIED and 4 个关键字段都没变:
    status = VERIFIED;  reviewed_by = prev.reviewed_by
elif prev.status == VERIFIED and 有字段变了:
    status = PENDING;   review_reason = 'CHANGED'
else:
    status = PENDING

dispatched_at = prev?.dispatched_at     # 已分发的保持可见
```

4 个关键字段：`mobile` / `countcard_lessons` / `timecard_expire` / `teacher`。

### 5.5 老师端可见性（一条 SQL 决定）

```sql
WHERE review_status = 'VERIFIED'
  AND teacher = #{staffName}
  AND snapshot_date = (最新一期)
```

**这条条件只写在 Mapper 的一个方法里**，不散落。

---

## 六、异常与错误码

```java
public record ApiResponse<T>(int code, String msg, T data) {}
```

| code | 触发 | 前端处理 |
| --- | --- | --- |
| 0 | 成功 | — |
| 400 | 参数校验失败 | 提示，不重试 |
| 401 | token 无效/过期 | 静默重登 |
| 403 | openid 不在白名单 / 角色无权 | 提示，不重试 |
| 404 | 记录不存在或无权访问 | 返回上一页 |
| 409 | 快照已更新 | 刷新 |
| 500 | 未预期异常 | 通用提示，日志留 traceId |

**两条硬规则**：任何异常不把堆栈、SQL、类名返回前端；`GlobalExceptionHandler` 兜底并记 traceId。

---

## 七、日志与脱敏

| 内容 | 打不打 |
| --- | --- |
| 请求路径、状态码、耗时 | ✅ |
| 请求体 / 响应体 | ❌（含手机号和话术） |
| 完整手机号 | ❌ 任何日志、异常消息、toast |
| SQL 参数 | ❌（prod 关闭） |
| traceId + userId | ✅ |

**唯一脱敏实现**：`MaskUtil.mask(mobile)` → `136****5177`。

---

## 八、安全设计

| 面 | 措施 |
| --- | --- |
| 身份 | 微信 code2Session + `app_user` 白名单 |
| 越权 | 单一入口 `TaskService.canAccess(user, memberId)` |
| SQL 注入 | MyBatis 全部 `#{}`，禁 `${}` |
| 传输 | 全站 HTTPS |
| 存储 | 全号只在 `followup_snapshot.mobile` 和 `followup_review_log.old_value`；后者无查询接口 |
| 配置 | appid / secret / db 密码 / jwt 密钥全走环境变量 |
| 网络 | MySQL 只监听 127.0.0.1；8080 只对 nginx 暴露 |

---

## 九、性能与资源预算

| 项 | 预算 | 依据 |
| --- | --- | --- |
| 并发 | 峰值 < 10 QPS | 3 个用户 |
| 接口响应 | P95 < 300ms | 单表 < 1000 行，全走索引 |
| 堆内存 | `-Xmx320m` | 可用 1350 MB，留 850 MB 余量 |
| 进程 RSS | < 600 MB | 堆 + metaspace 128m + 线程栈 |
| 连接池 | max 5 / min 1 | 2 核 |
| 表规模 | 快照 ~700 行/期；followup_log ~150 行/周 | 3 年 2 万行，**不需要分表** |
| 导入耗时 | < 5 秒 | 700 行，一次批量 upsert |

**内存是第一约束**。吃紧时优先压自己的堆，**不动 MySQL 全局参数**。

---

## 十、配置与部署

```ini
# /opt/hearth/db.env （600）
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=hearth
DB_USER=hearth
DB_PASSWORD=***

# /opt/hearth/app.env （600）
WX_APPID=***
WX_SECRET=***
JWT_SECRET=***
```

systemd 用 `EnvironmentFile` 注入，Spring 从环境变量读。**任何密钥都不进 git。**

发布：`mvn package` → `scp` 到 `app.jar.new` → `mv` 原子替换 → `systemctl restart`。
回滚：保留 `app.jar.prev`，一条命令换回。

---

## 十一、可观测性

| 项 | 做法 |
| --- | --- |
| 健康检查 | `GET /api/health` 返回 `{ok:true, version, db:"ok"}` |
| 日志 | `journalctl -u hearth`，按 traceId 串 |
| 数据库备份 | 每日 `mysqldump` → `/var/backups/hearth/`，保留 7 天 |
| 内存观察 | `systemctl status` + `free -m`，头一周每天看一眼 |

---

## 十二、设计层面明确不做

- ❌ 微服务 / 多模块拆分
- ❌ Redis（没有缓存需求）
- ❌ 消息队列（导入同步，5 秒跑完）
- ❌ 分库分表
- ❌ 单元测试覆盖率指标（只测三处）
- ❌ CI/CD 流水线

**测试只测三处**：脱敏、继承逻辑、权限。

---

## 十三、风险与未知

| 风险 | 概率 | 应对 |
| --- | --- | --- |
| 三体导出列名变化，导入失败 | 中 | 按列名映射，缺列记 null；导入前打印识别到的列 |
| 微信 code2Session 需要 appid/secret | 高 | D1 就要拿到，否则登录链路卡住 |
| 内存不足被 OOM Killer 杀 | 低 | 预算已留 850 MB；应急开关见 06 第 8.1 节 |
| 老师不用 | **中高** | 排期设了观察点，不靠猜 |
| 疑点规则命中率离谱 | 中 | 用真实数据校准，规则可配置 |

---

## 十四、供评审的决策点

| # | 决策 | 备选 | 影响 |
| --- | --- | --- | --- |
| 1 | MyBatis 而非 JPA | JPA | 换持久层等于重写 Mapper 层 |
| 2 | 引入 Spring Security | 手写拦截器 | 已定，为后续模块留位 |
| 3 | 导入用同一个 jar 的命令行模式 | 独立脚本 | 规则双份实现，迟早分叉 |
| 4 | 懒分发 | 导入时直接分发 | 影响「已分发仍可见」的保护条款 |
| 5 | 不做 Redis / MQ | 引入 | 部署与运维复杂度翻倍 |
| 6 | **未映射的 16 列存进 `extra_json`** | 直接丢弃 / 逐列加字段 | 见附录：丢弃会永久失去「取消次数/未到次数」这类潜在有用信号 |

---

## 附录 · CSV 列映射表（2026-11-09 用真实导出核对）

数据源：`跟进_待唤醒池.csv`（UTF-8 带 BOM，**423 条记录，38 列**）

> ⚠️ **解析器硬约束**：`话术` 列内含换行，文件 1270 行 ≠ 423 条记录。
> 必须用真正的 CSV 解析（引号内换行合法），**不能按行 split**。

### 已映射（22 列）

| CSV 列 | 目标字段 | 说明 |
| --- | --- | --- |
| 分配老师 | `teacher_name` + `staff_id` | 名字存快照，id 按名字查 `app_staff` |
| 分层 | `tier` | |
| 学员 | `member_name` | |
| 手机 | `mobile` | 入库前校验 11 位纯数字，否则 `MOBILE_BAD` |
| 活跃度 | `activity` | |
| 最后上课 | `last_class_date` | |
| 距末课天数 | `days_since_class` | |
| 到课次数 | `attend_count` | |
| 优先动作 | `queue` | 待确认：也可能对应 `sort_key` |
| 时间卡_卡种 | `timecard_kind` | |
| 时间卡_到期日 | `timecard_expire` | **4 个继承字段之一** |
| 时间卡_剩余天数 | `timecard_days_left` | |
| 次卡_卡种 | `countcard_kind` | |
| 次卡_剩余课时 | `countcard_lessons` | **4 个继承字段之一** |
| 次卡_剩余天数 | `countcard_days_left` | |
| 签单笔数 | `sign_count` | |
| 累计实收 | `total_paid` | |
| 最近签单 | `last_sign_date` | |
| 最近买的产品 | `last_product` | |
| 最近签单金额 | `last_sign_amount` | |
| 买过199 | `bought_199` | |
| 话术 | `talk_script` | 含换行，原样保存 |

### 未映射（16 列）→ 存 `extra_json`

`次卡_购买课时`、`次卡_已用课时`、`次卡_到期日`、`剩余课时合计`、`消耗情况`、
`有效合同数`、`近60天到期数`、`买过的产品`、`会员列表消费`、`跟过老师数`、
`跟过的老师`、`分配依据`、`取消次数`、`未到次数`、`首次上课`、`谈单提示`

**为什么不丢**：`取消次数` / `未到次数` / `首次上课` 是判断爽约风险的原料，
C2 做转化分析时很可能要用；`跟过的老师` 在老师离职交接时有用。
**为什么不逐列加字段**：现在没人查它们，加 16 个列会让表变宽、每列都要维护注释。

**做法**：`followup_snapshot` 加一列 `extra_json JSON NULL`，导入时把这 16 列原样塞进去。
以后谁要用，再 `ALTER TABLE` 提升成正式列，并用 SQL 回填。
| 6 | 只测三处 | 全面覆盖 | 两周内不可能全覆盖，假装覆盖更糟 |
