# C1 · 任务分解（Tasks）

> 版本 **v1.1** · 2026-11-09（v1.0 → v1.1：按 P0/C0 的实际产出校正状态与描述）
> 粒度：一个 task = 一次可独立验证的改动，0.5–2 小时。
> 判断标准：**做完能立刻回答「成了 / 没成」。**

**路径约定**：以下路径相对 `backend/src/main/java/cn/firedance/hearth/`；小程序路径相对 `miniprogram/`。

**前置**：P0（发布管子）✅ · C0（账号权限体系）✅ —— 两者都已完成，`DataScope` 可直接用。

> **v1.1 校正说明**：
> - T01 已完成，且实际是 **4 张表 + 3 个视图**（不是 5 张表）
> - T02 不再用迁移种数据：员工是会变的数据，用 `ops/admin.ps1` 录入
> - T19/T20 已由 C0 完成（小程序骨架、登录态、未开通页都在了）
> - T27/T28 的脚本 P0 就建好了，C1 只需重跑
> - `app_staff` 本地已有 3 人（老板/柒柒/三水），线上只有老板

---

## 任务总表

| # | 任务 | 改哪里 | 输入/输出 | 怎么验 | 依赖 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| T01 | 迁移：followup 四表三视图 | `db/migrations/V002__followup_init.sql` | — / 4 表 + 3 视图 | `show tables` 11 张（含平台表） | — | ✅ P0 已建 |
| T02 | 员工与账号 | `ops/admin.ps1` | — / 3 条员工 + 账号 | `admin.ps1 staff` 返回 3 行 | T01 | 🟡 本地 3 人，线上 1 人 |
| T03 | CSV 解析器 | `followup/importer/CsvParser.java` | CSV / `List<SnapshotDO>` | 20 行样例跑通，错列为 0 | T01 | ✅ |
| T04 | 疑点规则引擎 | `followup/importer/SuspectRuleEngine.java` | `SnapshotDO` / `Set<String>` | 6 条规则各 1 命中 + 1 不命中 | T03 | ✅ |
| T05 | 继承逻辑 | `followup/importer/InheritanceResolver.java` | 新旧两版 / `review_status` | 4 字段 × 变/不变 = 16 组合全过 | T03 | ⬜ |
| T06 | 导入主流程 | `followup/importer/ImportRunner.java` | `--import=xxx.csv` / 统计 | 全量导入成功，打印命中率 | T04,T05 | ⬜ |
| T07 | 核实分组统计 | `followup/web/ReviewController.java` | — / `pendingTotal`+`urgent`+`groups[]` | 返回 6 个分组；`urgent.count` 与手写 SQL 一致 | C0 ✅ | ⬜ |
| T08 | 分组名单 | 同上 | `reason` / `items[]` | 每页 20 条；`CLEAN` 人数与 SQL 一致 | T07 | ⬜ |
| T09 | 单条核实 | 同上 | `action`+`fields` / 新状态 | 四动作各验一次；改非白名单字段 400 | T08 | ⬜ |
| T10 | 整批动作 | 同上 | `reason`+`action`+`limit` / `affected` | 整批确认 200；重复调用 `affected=0` | T09 | ⬜ |
| T11 | 核实留痕 | `followup/mapper/ReviewLogMapper.xml` | — / `followup_review_log` | 每次动作新增 1 行，`old_value` 正确 | T09 | ⬜ |
| T12 | 今日待办 | `followup/web/TaskController.java` | — / 列表 + 进度 | **只返回 VERIFIED**；临期在最前 | C0 ✅ | ⬜ |
| T13 | 学员详情 | `followup/web/MemberController.java` | `id` / 详情+话术+历史 | 含话术全文；他人名下 404 | T12 | ⬜ |
| T14 | 提交跟进结果 | `followup/web/FollowupController.java` | `memberId`+`resultCode` | 同日重复提交是覆盖 | T13 | ⬜ |
| T15 | 信息有误退回 | 同上 | `id`+`note` / 新状态 | 回 PENDING 且 `TEACHER_FLAGGED` | T14 | ⬜ |
| T16 | 今日进度统计 | `followup/web/StatsController.java` | — / `done`+`total` | owner 额外返回 `byStaff` 与 `review` | T12 | ⬜ |
| T17 | 可见性单点收敛 | `followup/mapper/SnapshotMapper.xml` | — / 唯一查询 | 全仓库只有一处 `review_status='VERIFIED'` | T12 | ⬜ |
| T18 | 越权返回 404 | `followup/service/*` | 他人 memberId / 404 | 三水 token 请求柒柒名下 → 404 非 403 | T13 | ⬜ |
| T19 | 小程序骨架 | `app.js`、`utils/request.js`、`config/env.js` | — / 可运行空壳 | 开发者工具能打开，env 切换生效 | — | ✅ C0 已建 |
| T20 | 小程序登录 | `utils/auth.js`、`pages/login` | — / token 存本地 | 登录后请求自动带头 | T19 | ✅ C0 已建 |
| T21 | 待办列表页 | `pages/tasks/` | — / 列表 | 显示真实数据，临期在最上 | T20 ✅,T12 | ⬜ |
| T22 | 学员详情页 | `pages/member/` | `id` / 详情 | 结果码可提交，进度更新 | T21,T14 | ⬜ |
| T23 | 核实工作台 | `pages/review/` | — / 折叠分组 | 完成一次整批确认，折叠不跳页 | T20 ✅,T10 | ⬜ |
| T24 | 我的页补充统计 | `pages/me/` | — / 统计 | teacher 与 owner 字段不同 | T20 ✅,T16 | 🟡 基础版已有 |
| T25 | 三态统一 | 各页面 | — | 断网可重试；空数据有提示 | T21–T24 | ⬜ |
| T26 | 单测三处 | `src/test/java/...` | — / 3 组用例 | 脱敏、继承、权限全绿 | T05,T17,T18 | 🟡 脱敏与权限已有 |
| T27 | 冒烟脚本 | `ops/smoke.ps1` | 环境参数 / 通过失败 | 本地与线上各跑通一次 | T26 | ✅ P0 已建（C1 补用例） |
| T28 | 发布并三层验证 | `ops/deploy.ps1` | — / 线上可访问 | systemd active → 本机 8080 → 外网 `/hearth/` 全绿 | T27 | ✅ P0 已建 |

---

## 关键任务详情

### T05 · 继承逻辑（最容易做错）

- **判定**：4 个关键字段任一变化 → PENDING + `CHANGED`；全不变且上期 VERIFIED → 继承
- **关键字段**：`mobile` / `countcard_lessons` / `timecard_expire` / `teacher`
- **保护条款**：`dispatched_at` 从上一期继承，**不因退回 PENDING 而清空**
- **验收**：4 字段各构造「变/不变」，共 16 种组合，逐一断言

### T10 · 整批动作（防误操作）

- 只处理当前 `PENDING`；服务端自己遍历，**前端不传 ID 数组**
- **验收**：整批确认 200 → `affected=200`；再调一次 → `affected=0`

### T17 · 可见性单点收敛

- 全仓库只允许**一处**出现 `review_status = 'VERIFIED'`
- **验收**：`grep -r "review_status" backend/` 业务查询只有 1 处
- **为什么**：散落成三处，就一定会漏一处，然后老师看到没核过的数据

### T18 · 越权返回 404（不是 403）

- C0 的 `DataScopeService` 提供 `all` / `self(staffId)`
- 老师请求别人名下的 memberId：**查不到就是 404**，不告诉他"存在但你没权限"
- **验收**：三水 token 请求柒柒名下学员 → 404

---

## 执行顺序

```
T03 → T04 → T05 → T06          （数据线，可先跑）
T07 → T08 → T09 → T10 → T11    （核实线）
T12 → T13 → T14 → T15 → T16 → T17 → T18 （老师线）
T21 → T22 → T23 → T24 → T25              （前端线）
T26 → T27 → T28                           （收尾）
```

---

## 完成度

| 总数 | 已完成 | 部分完成 | 未开始 |
| --- | --- | --- | --- |
| 28 | 7（T01,T03,T04,T19,T20,T27,T28） | 3（T02,T24,T26） | 18 |
