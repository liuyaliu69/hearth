# C1 · 今日跟进（模块 `followup`）

> 状态：**文档定稿，等待 P0 与 C0 完成后开始编码**
> 周期产出：平台第一个业务模块（v1.0.0）

---

## 一句话目标

让柒柒、三水、老板三个人，在手机上完成这条链：

> **老板先核实数据 → 系统把核过的人分发给老师 → 老师照着打并记录 → 老板看到进度**

替代 `python tools\followup_log.py log <手机号> <结果码>` 这条命令行。

**否证条件**：两周后如果柒柒宁可看 CSV 也不打开它，目标即失败，与代码完成度无关。

---

## 为什么是这件事

上个月没执行下去，**不是老师嫌工具难用，是数据不准让老师不敢用**。

所以产品的第一价值不是「方便」，是「可信」——先加一道人工核实闸门，核过才放给老师。

---

## 文档（SDD 三阶段）

| 阶段 | 文档 | 状态 |
| --- | --- | --- |
| Requirements | [spec.md](spec.md) | v0.2 |
| Design | [design.md](design.md) | v1.0 |
| Design 附件 | [api.md](api.md)（10 个接口）、[wireframe.md](wireframe.md)（4 屏） | v0.2 / v0.1 |
| **Tasks** | [tasks.md](tasks.md)（32 条） | v1.0 |
| 评审 | [review-checklist.md](review-checklist.md) | 待逐条确认 |
| 排期 | [schedule.md](schedule.md) | 已调整（本地优先） |

---

## 关键决策（已定）

| # | 决策 |
| --- | --- |
| 1 | 持久层 MyBatis，不用 JPA |
| 2 | 引入 Spring Security（平台要为后续模块留扩展位） |
| 3 | 导入用同一个 jar 的命令行模式，不写独立脚本 |
| 4 | 懒分发：老师第一次被查到时才写 `dispatched_at` |
| 5 | 优先级：**临期 > 疑点** |
| 6 | 老师任务量不设上限，系统不做管理 |
| 7 | 核实只看 4 个关键字段的变化来决定是否重新待核 |

---

## 未闭环

| # | 事项 | 谁 |
| --- | --- | --- |
| 1 | 评审清单逐条确认 | 老板 |
| 2 | C0 的账号/权限体系先落地（含 `app_user` 与 `staff` 分离） | AI |
| 3 | P0 发布管子完成 | AI |
| 4 | 微信 `appid` / `secret` 填入服务器 `app.env` | 老板 |
| 5 | 小程序备案 → 配 request 合法域名（体验版才需要） | 老板 |

---

## 与平台的关系

- 代码：`backend/src/main/java/cn/firedance/hearth/followup/` + `miniprogram/pages/followup/`
- 数据：库 `hearth`，表以 `followup_` 为前缀（`app_*` 是 C0 的平台表）
- 接口：`/api/followup/**`
