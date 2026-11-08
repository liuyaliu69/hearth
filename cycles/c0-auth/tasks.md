# C0 · 任务分解（Tasks）

> 版本 v1.0 · 2026-11-07
> 粒度：一个 task = 一次可独立验证的改动，0.5–2 小时。
> 路径相对 `backend/src/main/java/cn/firedance/hearth/`。

**前置**：P0 已完成（骨架、Flyway、发布、冒烟都在）。

---

## 任务总表

| # | 任务 | 改哪里 | 怎么验 | 依赖 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T01 | 加 JOSE 依赖（JWT 用 Spring 自带） | `backend/pom.xml` | `mvn dependency:tree` 看到 spring-security-oauth2-jose | — | ⬜ |
| T02 | 当前用户模型 | `auth/CurrentUser.java`、`auth/UserContext.java` | 单测：从空上下文取报错 | T01 | ⬜ |
| T03 | JWT 签发与解析 | `auth/JwtUtil.java` | 单测：签发→解析一致；过期→空 | T02 | ⬜ |
| T04 | 微信登录适配（真实 + mock） | `auth/WechatService.java` + 两个实现 | mock：`code=x` → `mock_x`；真实：secret 为空时不注册 | T01 | ⬜ |
| T05 | 查用户与角色 | `auth/AuthMapper.java` + XML | 直连库造一条记录，能查出 roles | T04 | ⬜ |
| T06 | 登录编排 | `auth/AuthService.java` | 无用户→403 带 openid；有→token | T03,T05 | ⬜ |
| T07 | 登录接口 | `auth/AuthController.java` | `POST /api/auth/login` 返回统一 JSON | T06 | ⬜ |
| T08 | 当前用户接口 | 同上 | `GET /api/auth/me` 返回姓名/角色/needMobile | T07 | ⬜ |
| T09 | JWT 过滤器 | `auth/JwtAuthenticationFilter.java` | 无 token→401；错 token→401；停用→401 | T03,T05 | ⬜ |
| T10 | 安全规则改造 | `config/SecurityConfig.java` | `/api/admin/**` 无 owner 角色→403 | T09 | ⬜ |
| T11 | 数据范围上下文 | `auth/DataScope.java`、`auth/DataScopeService.java` | 单测：owner=all，teacher=self | T02 | ⬜ |
| T12 | 管理接口：列表 | `auth/admin/AdminUserController.java` | 有手机号/无手机号都能列出 | T10 | ⬜ |
| T13 | 管理接口：开通 | 同上 | 新 openid 立即可登录，无需重启 | T12 | ⬜ |
| T14 | 管理接口：停用/改角色 | 同上 | 停用后旧 token 立刻 401 | T13 | ⬜ |
| T15 | 账号管理脚本 | `ops/admin.ps1` | `list` / `create` / `disable` / `role` / `bootstrap` | T14 | ⬜ |
| T16 | 小程序：登录态与请求改造 | `miniprogram/utils/auth.js`、`utils/request.js` | token 自动带；401 自动重登一次 | T08 | ⬜ |
| T17 | 小程序：未开通页 + 手机号提醒 | `miniprogram/pages/login/`、`pages/me/` | 未开通显示 openid 可复制；缺手机号弹提醒 | T16 | ⬜ |
| T18 | 测试补齐 | `src/test/java/...` | JWT / 登录与停用 / 数据范围 三组全绿 | T03,T06,T11 | ⬜ |
| T19 | 冒烟脚本加登录用例 | `ops/smoke.ps1` | 本地与线上都跑通 | T18 | ⬜ |
| T20 | 发布并验收 8 条 DoD | `ops/deploy.ps1` | 逐条走一遍 spec 第九节 | T19 | ⬜ |

---

## 关键任务细节

### T03 · JWT 签发与解析

- 用 Spring 自带 `NimbusJwtEncoder` / `NimbusJwtDecoder`（`spring-security-oauth2-jose`），不引第三方 JWT 库
- HS256，密钥来自 `JWT_SECRET`；为空时生成临时随机密钥并 WARN（重启失效，仅开发）
- 载荷**只放** `sub=userId`、`iat`、`exp`——**不放角色**（停用/改角色要立即生效）

### T04 · 微信适配

- `RealWechatService`：`@ConditionalOnProperty` 要求 `app.wx.secret` 非空
- `MockWechatService`：secret 为空时启用，`openid = "mock_" + code`
- **两条路径返回结构一致**，之后的逻辑一行不分叉

### T06 · 登录编排（含未开通返回 openid）

- 查到用户但 `status != ACTIVE` → 403，`data` 里带 openid（供未开通页展示）
- 查到且 ACTIVE → 签 token，返回 `needMobile`（staff.mobile 是否为空）

### T09 · 过滤器

- 每次请求都查库（`findActiveById`），**不信任 token 里的状态**——停用立即生效
- 解析失败不抛异常，清空上下文交给 401 处理器

### T11 · 数据范围

- `DataScope(boolean all, Long staffId)`
- Mapper 用参数化条件 `WHERE (#{scope.all} = 1 OR staff_id = #{scope.staffId})`，不拼 SQL

### T15 · 管理脚本

- `bootstrap`：第一个 owner 直接写库（系统里没账号时，没人能调管理接口）
- 其余子命令走 HTTP 需要 owner token；**为简化，脚本直连库**（运维工具，跑在有凭据的机器上）
- 接口仍然保留（未来小程序管理页用，也是 DoD 的验收方式）

---

## 执行顺序

```
T01 → T02 → T03 ┐
T04 → T05 → T06 → T07 → T08 ┤
T09 → T10 → T12 → T13 → T14 → T15
T11 ────────────────────────┘
T16 → T17
T18 → T19 → T20
```

---

## 完成度

| 总数 | 已完成 | 阻塞 |
| --- | --- | --- |
| 20 | 0 | 0 |
