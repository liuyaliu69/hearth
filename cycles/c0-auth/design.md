# C0 · 账号与权限体系 · 设计说明书

> 版本 v0.1 · 2026-11-07 · 待评审
> 依据：`spec.md` v0.1
> 本文回答「怎么实现」，不是「做什么」。

---

## 一、模块划分

```
backend/src/main/java/cn/firedance/hearth/
├── common/                     已有：ApiResponse / ErrorCode / HealthController / MaskUtil
├── config/
│   └── SecurityConfig.java     ← 改造：加 JWT 过滤器与路径规则
└── auth/                       ← 本周期新增
    ├── AuthController.java         /api/auth/login、/api/auth/me
    ├── AuthService.java            登录编排
    ├── WechatService.java          接口：code → openid
    ├── RealWechatService.java      真实实现（appid+secret 齐了才启用）
    ├── MockWechatService.java      开发实现（secret 为空时启用）
    ├── JwtUtil.java                签发 / 解析
    ├── JwtAuthenticationFilter.java
    ├── CurrentUser.java            record(userId, name, staffId, roles)
    ├── UserContext.java            从 SecurityContext 取当前用户
    ├── DataScope.java              数据范围上下文（all / self）
    ├── AuthMapper.java             查用户+角色
    └── admin/
        ├── AdminUserController.java
        └── AdminUserService.java
```

**分层不变**：Controller 只做参数与转发，业务在 Service，SQL 在 Mapper。

---

## 二、登录实现

### 2.1 WechatService 的两种实现（按配置自动切换）

| 条件 | 启用的实现 | 行为 |
| --- | --- | --- |
| `app.wx.appid` 与 `app.wx.secret` **都非空** | `RealWechatService` | 调 `https://api.weixin.qq.com/sns/jscode2session` |
| 任一为空 | `MockWechatService` | `openid = "mock_" + code`（code 限制 `[A-Za-z0-9_-]{1,64}`） |

用 `@ConditionalOnProperty` + 一个 `WechatServiceConfig` 决定注入哪个。
**两条路径的返回结构完全一致**，之后的逻辑（查用户、签 token）一行都不分叉。

> mock 的意义：没有 AppSecret 也能把整条链路（含小程序端）跑通并验收，
> 等 secret 配好，**只切换一次配置**，业务代码零改动。

### 2.2 登录流程

```
POST /api/auth/login {code}
  → wechatService.code2openid(code)
  → authMapper.findActiveUserByOpenid(openid)
       ├─ 无 / status != ACTIVE → 403 {"code":403,"msg":"无权限，请联系管理员"}
       └─ 有 → 查角色 → jwtUtil.issue(userId)
  → 更新 last_login_at（异步/忽略失败）
  → 返回 {token, user:{id,name,staffId,roles,expireAt}}
```

### 2.3 JWT 约定

| 项 | 值 |
| --- | --- |
| 算法 | HS256 |
| 载荷 | **只放 `sub`(userId) + `iat` + `exp`**，不放角色 |
| 有效期 | 7 天 |
| 密钥 | 环境变量 `JWT_SECRET`；为空时生成临时随机密钥并打 WARN（重启后 token 失效，仅影响开发） |

**角色不进 token** 的理由：改角色/停用要立即生效，放进去就得等 7 天。

---

## 三、鉴权链

```
请求 → JwtAuthenticationFilter
        ├─ 无 Authorization 头 → 放行给 Security（后续按规则拦）
        └─ 有 Bearer → 解析
             ├─ 解析失败/过期 → 清空上下文，交给 401 处理
             └─ 成功 → 查库 findActiveUserById（含角色）
                   ├─ 无或已停用 → 清空上下文（★ 停用立即生效）
                   └─ 正常 → 构造 Authentication(authorities = ROLE_OWNER / ROLE_TEACHER)
```

**路径规则**（`SecurityConfig`）

| 路径 | 规则 |
| --- | --- |
| `/api/health` | permitAll |
| `/api/auth/login` | permitAll |
| `/api/admin/**` | hasRole('OWNER') |
| 其余 | authenticated |

401 / 403 仍走现有的统一 JSON 处理器（昨天已实现）。

---

## 四、数据范围

```java
public record DataScope(boolean all, Long staffId) {
    public static DataScope all() { ... }
    public static DataScope self(Long staffId) { ... }
}
```

- `DataScopeService.current()`：owner → `all()`；teacher → `self(user.staffId)`
- 业务 Mapper 用**参数化条件**，不拼字符串：

```sql
WHERE (#{scope.all} = 1 OR staff_id = #{scope.staffId})
```

**为什么用参数而不是动态 SQL**：动态 SQL 容易在某个分支漏掉条件；
参数化写法只有一条 SQL，漏不掉。

---

## 五、管理接口

| 接口 | 入参 | 出参 |
| --- | --- | --- |
| `GET /api/admin/users` | — | `[{id,name,staffId,staffName,roles,status,lastLoginAt}]` |
| `POST /api/admin/users` | `{staffId, openid, roleCodes[]}` | 新建的 user |
| `PATCH /api/admin/users/{id}` | `{status?, roleCodes?}` | 更新后的 user |

规则：
- 不能停用自己（防锁死）
- `openid` 唯一，冲突返回 400
- `staffId` 必须存在于 `app_staff`
- `roleCodes` 必须是 `app_role` 里已有的

---

## 六、账号管理工具：`ops/admin.ps1`

**两个决定，请你确认或推翻：**

### 决定 1：第一个 owner 用 SQL 种（bootstrap）

系统里一个账号都没有时，没人能登录，也就没人能调管理接口——鸡生蛋问题。

```
.\ops\admin.ps1 bootstrap -OpenId mock_owner1 -Name 老板
```

直接写库（`app_user` + `app_user_role`），只用于**第一次**。之后一切走接口/脚本。

### 决定 2：日常管理用 SQL 而不是走 HTTP

```
.\ops\admin.ps1 list
.\ops\admin.ps1 create -OpenId mock_qixi1 -StaffName 柒柒 -Role teacher
.\ops\admin.ps1 disable -Name 柒柒
.\ops\admin.ps1 role -Name 三水 -Role teacher
```

理由：这个脚本是**运维工具**，跑在有数据库凭据的机器上。走 HTTP 需要先拿 owner token，
而拿 token 又要先有账号——绕一圈没有收益。**接口仍然保留**（未来小程序管理页要用，也用于 DoD 验收）。

---

## 七、小程序侧改动

| 文件 | 改动 |
| --- | --- |
| `utils/auth.js` | 新增：`ensureLogin()` 有 token 先 `/auth/me`，失败静默 `wx.login` |
| `utils/request.js` | 改动：自动带 `Authorization`；401 时清 token 并重登一次 |
| `app.js` | `onLaunch` 调用 `ensureLogin()` |
| `pages/login/` | 无权限提示页（显示 openid 脱敏后 4 位） |
| `pages/me/` | 改造：显示姓名、角色、退出登录 |

---

## 八、测试（只测三处原则里的两处）

| 测什么 | 用例 |
| --- | --- |
| JWT | 签发→解析一致；过期→解析失败；篡改→失败 |
| 登录与停用 | 库里无此人→403；停用后→401；正常→200 |
| 数据范围 | owner 的 scope.all=true；teacher 的 staffId 正确；staffId 为空时拒绝 |

脱敏已在 P0 测过，不重复。

---

## 九、配置项

```yaml
app:
  wx:
    appid:   ${WX_APPID:}
    secret:  ${WX_SECRET:}     # 空 → 启用 mock
  jwt:
    secret:  ${JWT_SECRET:}    # 空 → 临时随机
    ttl-days: 7
```

服务器 `/opt/hearth/app.env` 里补 `WX_SECRET` 与 `JWT_SECRET`（600）。

---

## 十、风险

| 风险 | 应对 |
| --- | --- |
| mock 与真实逻辑分叉 | 只替换 code→openid 一步，其余代码共用；上线前用真实 secret 跑一次登录 |
| JWT_SECRET 未配导致重启后 token 全失效 | 部署检查清单里加一条；server 的 `app.env` 一次性配好 |
| 停用后旧 token 仍可用 | 已设计为每次查库，**不存在这个窗口** |
| 管理脚本直连库绕过业务规则 | 脚本只做「账号」这一件事，不碰业务数据；接口与脚本写同一套约束 |

---

## 十一、供评审的决策点

| # | 决策 | 我的默认 | 备选 |
| --- | --- | --- | --- |
| 1 | 无 AppSecret 时启用 mock 登录 | 是 | 死等 secret（阻塞 C0） |
| 2 | 账号管理用命令行直连库 | 是 | 做小程序管理页（工作量大） |
| 3 | 第一个 owner 用 SQL bootstrap | 是 | 提供一个无需登录的初始化接口（有安全风险） |
| 4 | 角色不进 JWT | 是 | 放进去（停用/改角色要等 7 天） |
| 5 | 数据范围用参数化 SQL | 是 | 动态拼 SQL |
