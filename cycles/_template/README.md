# 周期模板

> 复制本目录为 `cycles/cN-<英文短名>/`，按顺序填。
> 模型对齐 **Spec-Driven Development**：`Requirements → Design → Tasks`。

---

## 三阶段与文件

| 阶段 | 文件 | 回答什么 | 什么时候写 |
| --- | --- | --- | --- |
| 封面 | `README.md` | 目标、状态、导航 | 周期第一天 |
| **Requirements** | `spec.md` | 做什么、为什么、做到什么算完 | 编码前 |
| **Design** | `design.md`（+ `api.md`、`wireframe.md`） | 怎么做 | 编码前 |
| **Tasks** | `tasks.md` | 拆成 0.5–2 小时的可验证单元 | 编码前 |
| 评审 | `review-checklist.md` | 逐条确认 | 编码前 |
| 排期 | `schedule.md` | 哪天做什么（项目管理，**不是执行依据**） | 编码前 |
| 复盘 | `retro.md` | 三个数 + 该固化什么 | 周期结束 |

---

## 顺序

1. `README.md` —— 一句话目标 + **否证条件**
2. `spec.md` —— 范围与**非目标**（非目标写不出来，这个周期一定做不完）
3. `design.md` —— 架构与关键流程
4. `tasks.md` —— 拆任务（判断标准：做完能立刻说「成了/没成」）
5. `review-checklist.md` —— 逐条确认
6. 评审通过 → 冻结 v1.0 → 开始编码
7. 周期结束 → `retro.md`

---

## 三条纪律

1. **周期内需求冻结**：新想法进 backlog，不改当期范围
2. **spec 里不许出现实现细节**：出现「用 MyBatis」「加索引」就说明越界了
3. **tasks 不许写到日程粒度**：「D3 做核实接口」不是任务
