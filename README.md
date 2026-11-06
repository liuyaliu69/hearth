# 火花私域 · Hearth

> 给自己舞蹈工作室（FireDance）做的私域运营平台。
> **一个 Spring Boot 应用 + 一个小程序**，按周期逐个加入模块，每个周期发布一个版本。

---

## 当前阶段：P0 已完成 → 下一步 C0

**P0 的产出不是功能，是「改一次代码 → 发布 → 验证 → 回滚」这条管子。它已经通了，并在线上跑过一次真实发布。**

| 状态 | 项 |
| --- | --- |
| ✅ | 仓库结构（多模块单应用） |
| ✅ | 规则文件（AGENTS.md + Trae rules，8 条硬规则） |
| ✅ | 平台约定文档（7 份，含小程序操作手册） |
| ✅ | 周期模板（含 tasks 层） |
| ✅ | 数据库版本化（Flyway + V001/V002，线上已验证） |
| ✅ | 后端骨架 + 探针接口（本地 + 线上都通） |
| ✅ | 小程序骨架 + 诊断页（开发者工具已验证） |
| ✅ | 测试（脱敏 7 例）+ 冒烟脚本（本地/线上三连） |
| ✅ | 备份（hearth 账号，无需 root）+ **恢复演练已真跑** |
| ✅ | 手工部署上线（systemd + nginx `/hearth/`） |
| ⬜ | **一键发布脚本 `ops/deploy.ps1`**（唯一剩余项） |

**P0 验收**：一次完整变更走通 —— 改一次 SQL → 发布后端 → 前端真的看到 → 测试证明它对 → 失败可回滚。

---

## 周期与版本

| 周期 | 版本 | 主题 | 状态 |
| --- | --- | --- | --- |
| **P0** | — | 工程地基（不发布） | ✅ 完成 |
| **C0** | v0.1.0 | 账号 / 角色 / 权限体系 | 🚧 明天开始 |
| **C1** | v1.0.0 | 跟进模块（核实 → 分发 → 记录） | 文档已定稿 |
| C2 | v2.0.0 | 待定 | — |

---

## 目录

| 目录 | 放什么 |
| --- | --- |
| `platform/docs/` | 跨周期约定：定位、技术、命名、发布、术语、部署、小程序操作 |
| `platform/decisions/` | ADR：每个「为什么这么定」 |
| `backend/` | 唯一的 Spring Boot 应用（多模块共存） |
| `miniprogram/` | 唯一的小程序 |
| `db/migrations/` | 编号的 SQL 迁移 |
| `ops/` | 发布、本地 nginx、备份 |
| `cycles/` | 每个周期一个目录，只放文档 |

---

## 怎么开始

1. 用 **Trae CN** 打开本目录
2. 先读 [`AGENTS.md`](AGENTS.md)——给 AI 的规则，**必读**
3. 再读 [`platform/docs/01-平台定位.md`](platform/docs/01-平台定位.md)
4. 当前周期：[`cycles/c1-followup/README.md`](cycles/c1-followup/README.md)

---

## 本地环境速查

```powershell
# 本地 nginx（模拟线上 /hearth/ 前缀，监听 8081）
powershell -ExecutionPolicy Bypass -File .\ops\local-nginx.ps1 -Action start

# 后端（P0.6 之后）
mvn -f backend\pom.xml spring-boot:run
```

| 项 | 值 |
| --- | --- |
| 本地入口 | `http://localhost:8081/hearth/` → 后端 `8080` |
| 线上入口 | `https://www.huohuawd.cn/hearth/` → ECS `8080` |
| 服务器 | `101.200.14.51`（与小智、游戏站同机，内存紧张） |

---

## 一条事故教训

2026-11-06，仓库内容因为「先删后验证」被误删过一次。规则已写进 `AGENTS.md` 第三条硬规则：

> **复制 → 验证新副本可用 → 才删除旧的。**
