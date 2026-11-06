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
# 方式一：让应用启动时自动执行（推荐，和线上一致）
mvn -f backend\pom.xml package -DskipTests
java -jar backend\target\hearth.jar        # 需要 DB_* 环境变量

# 方式二：手工执行（排障用）
mysql -u hearth -p hearth < db/migrations/V001__platform_init.sql

# 核对
mysql -u hearth -p hearth -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
mysql -u hearth -p hearth -e "SHOW FULL TABLES;"
```

---

## 文件位置与打包方式（别改错了）

**真相在 `db/migrations/`**（人读方便、有 README、git 历史清晰）。

构建时由 `backend/pom.xml` 的 `<resources>` 复制进 jar 的 `db/migration/`：

```xml
<resource>
  <directory>${project.basedir}/../db/migrations</directory>
  <targetPath>db/migration</targetPath>
  <includes><include>**/*.sql</include></includes>
</resource>
```

Flyway 读的是 `classpath:db/migration`，所以**不要**把 SQL 手动挪进 `backend/src/main/resources/`——
那样会出现两份，改了一份另一份不动。

---

## 已经踩过的坑

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| `Found non-empty schema "hearth" without schema history table` | 表是手工建的，Flyway 不认 | 已配 `baseline-on-migrate: true` + `baseline-version: 0` |
| 日志里一堆 `Table 'xxx' already exists` | 同上：V001/V002 用 `CREATE TABLE IF NOT EXISTS` 重跑 | 无害，警告而已 |
| `MySQL 8.4 is newer than this version of Flyway` | Spring Boot 3.3.5 带的 Flyway 10.10 最高支持 MySQL 8.1 | 目前功能正常，等升级 Flyway 再消
