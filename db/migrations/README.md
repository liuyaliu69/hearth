# 数据库迁移

> 规则见 [`platform/docs/02-技术约定.md`](../../platform/docs/02-技术约定.md) 第四节。

---

## 铁律

1. **只增不改**：已提交的迁移文件不修改。写错了再写一个 `VNNN+1` 去修
2. **一文件一件事**：一个迁移只做一类变更
3. **命名**：`VNNN__简短描述.sql`，三位序号从 `001` 开始
4. **上线前必须备份**：Flyway 社区版没有 undo，回滚靠备份恢复

---

## 现有迁移

| 文件 | 内容 | 已在本地执行 |
| --- | --- | --- |
| `V001__platform_init.sql` | 平台主数据：`app_shop` / `app_staff` / `app_user` / `app_role` / `app_user_role` / `app_member` | ✅ 2026-11-06 |
| `V002__followup_init.sql` | 跟进模块：`followup_snapshot` / `followup_flag` / `followup_review_log` / `followup_log` + 3 视图 | ✅ 2026-11-06 |

> `V001` 内含门店与角色的种子数据（幂等写法），重复执行不会重复插入。

## 后续规划（未创建）

| 预计 | 内容 | 周期 |
| --- | --- | --- |
| `V003` | 活动 / 优惠券 | C2 |
| `V004` | 内容素材 | C3 |
| `V005` | 三体镜像表（订单 / 卡 / 预约 / 课消） | 分析需要时 |

**序号一旦提交就不能改**，新周期往后排。

---

## 怎么验证一次迁移

```powershell
# 建库（首次）
#   1. 用 root 建库和账号
#   2. 用 hearth 账号执行迁移
mysql -u hearth -p hearth < db/migrations/V001__platform_init.sql
mysql -u hearth -p hearth < db/migrations/V002__followup_init.sql

# 核对
mysql -u hearth -p hearth -e "SHOW FULL TABLES;"
mysql -u hearth -p hearth -e "SELECT code,name FROM app_shop; SELECT code,name FROM app_role;"
```

Flyway 接入后（P0.5），改为：

```powershell
mvn -f backend\pom.xml flyway:migrate
mysql -u hearth -p hearth -e "SELECT version, description, success FROM flyway_schema_history;"
```

> 注意：Flyway 接入后，上面两个文件需要移到 `backend/src/main/resources/db/migration/`，
> 或通过配置指向 `db/migrations/`。迁移这一步在 P0.5 一次做完。
