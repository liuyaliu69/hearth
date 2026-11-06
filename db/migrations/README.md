# 数据库迁移

> 规则见 [`platform/docs/02-技术约定.md`](../../platform/docs/02-技术约定.md) 第四节。

---

## 铁律

1. **只增不改**：已提交的迁移文件不修改。写错了再写一个 `VNNN+1` 去修
2. **一文件一件事**：一个迁移只做一类变更（加表 / 加字段 / 加索引）
3. **命名**：`VNNN__简短描述.sql`，三位序号从 `001` 开始
4. **上线前必须备份**：Flyway 社区版没有 undo，回滚靠备份恢复

---

## 规划中的迁移

| 文件 | 内容 | 属于 |
| --- | --- | --- |
| `V001__init.sql` | 平台基础：`app_user` / `app_role` / `app_user_role` | C0 |
| `V002__followup_init.sql` | 跟进模块五张表 + 视图 | C1 |
| `V003__seed_staff.sql` | 初始账号占位 | C0 / C1 |

> 序号一旦提交就不能改。新周期要动表，往后排。

---

## 怎么验证一次迁移

```powershell
mvn -f backend\pom.xml flyway:migrate

mysql -u hearth -p hearth -e "select version, description, success from flyway_schema_history order by installed_rank;"
mysql -u hearth -p hearth -e "show tables;"
```

失败时先看 `flyway_schema_history` 最后一条的 `success` 字段。
