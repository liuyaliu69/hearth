# C1 · 接口契约

> **v0.3** · 2026-11-10
> v0.2 → v0.3：核实分组新增 `CARD_NO_CLASS`（买了课包没来上），与 `spec.md` v0.3 对齐。
> 原则：**能少一个就少一个**。每多一个接口，就多一处权限漏洞。

---

## 零、通用约定

- **Base URL（分环境）**
  - 本地开发：`http://127.0.0.1:8080`
  - 线上：`https://www.huohuawd.cn/hearth`（nginx `location /hearth/` → `proxy_pass http://127.0.0.1:8080/`，尾斜杠剥掉前缀）
- **调用方式**：`wx.request`，不走 `callContainer`
- **小程序后台**：`request 合法域名 = https://www.huohuawd.cn`（**只有体验版/正式版需要**；开发者工具勾选「不校验合法域名」即可）

| 环境 | Base URL | 路由 | 谁用 |
| --- | --- | --- | --- |
| dev（开发者工具 / 真机调试） | `http://127.0.0.1:8080`；真机用 `http://<电脑局域网IP>:8080` | 无前缀 | 你自己开发时 |
| prod（体验版 / 正式版） | `https://www.huohuawd.cn/hearth` | nginx 剥掉 `/hearth/` | 柒柒 / 三水 |

> 两种环境下后端收到的路径**完全一致**（都是 `/api/...`），本地验证过的路由在线上同样成立。

- **鉴权**：`Authorization: Bearer <token>`，除 `login` 与 `health` 外全部必需
- **统一响应**：`{ "code": 0, "msg": "", "data": {} }`

| code | 含义 | 前端处理 |
| --- | --- | --- |
| 0 | 成功 | — |
| 400 | 参数错误 | 提示，不重试 |
| 401 | 未登录 / token 失效 | 静默重 login |
| 403 | 无权限 / 角色不够 | 提示，不重试 |
| 404 | 记录不存在或不属于你 | 返回上一页 |
| 409 | 快照已过期 | 提示刷新 |

**脱敏**：`mobile` 一律在后端转成 `前3****后4`，全号不出服务端。

---

## 一、POST /api/auth/login

```json
{ "code": "081AbCd..." }
```

```json
{
  "code": 0,
  "data": {
    "token": "eyJhbGciOi...",
    "expireAt": "2026-11-13T10:00:00+08:00",
    "user": { "id": 1, "name": "柒柒", "role": "teacher" }
  }
}
```

`403` = openid 不在 `app_user` 表。JWT HS256，7 天有效，载荷只放 `userId` / `role`。

---

## 二、GET /api/followup/review/groups　（仅 owner）

```json
{
  "code": 0,
  "data": {
    "snapshotDate": "2026-10-31",
    "pendingTotal": 87,
    "urgent": { "label": "临期且待核实", "count": 23 },
    "groups": [
      { "reasonCode": "TEACHER_LEFT",  "label": "归属老师已离职", "count": 12 },
      { "reasonCode": "SILENT_BALANCE","label": "有余额但半年没来", "count": 41 },
      { "reasonCode": "CARD_NO_CLASS", "label": "买了课包没来上",  "count": 6 },
      { "reasonCode": "MOBILE_BAD",    "label": "手机号异常",     "count": 6 },
      { "reasonCode": "STATE_CONFLICT","label": "状态矛盾",       "count": 9 },
      { "reasonCode": "NO_SHOW_199",   "label": "199 未到课",     "count": 19 },
      { "reasonCode": "CLEAN",         "label": "无疑点（可整批通过）", "count": 528 }
    ]
  }
}
```

`CLEAN` 是虚拟分组（没命中任何规则），排最后。`urgent` 跨疑点，优先级最高。

---

## 三、GET /api/followup/review/items　（仅 owner）

| 参数 | 说明 |
| --- | --- |
| `reason` | 上一步的 `reasonCode`，或 `CLEAN`，或 `URGENT` |
| `cursor` | 分页游标，首页不传 |

```json
{
  "code": 0,
  "data": {
    "total": 12,
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "items": [
      {
        "id": 101,
        "name": "李菡蕾",
        "mobileMasked": "136****5177",
        "teacher": "曼曼",
        "lessonsLeft": 40,
        "daysLeft": 7,
        "lastClassDate": "2024-11-20",
        "reasons": ["TEACHER_LEFT", "SILENT_BALANCE"],
        "queue": "RED"
      }
    ]
  }
}
```

每页 20 条。**列表不返回话术**（可能几百字），详情才给。

---

## 四、POST /api/followup/review/items/{id}　（仅 owner）

```json
{ "action": "FIX", "fields": { "countcardLessons": 32 }, "note": "实际剩 32 节" }
```

| action | fields | 结果 |
| --- | --- | --- |
| `CONFIRM` | 无 | → VERIFIED |
| `FIX` | 只允许 4 个关键字段 | → VERIFIED，原值写 `followup_review_log` |
| `REJECT` | 无 | → REJECTED |
| `PARK` | 无 | → PARKED |

**允许修正的 4 个字段**：`mobile` / `countcardLessons` / `timecardExpire` / `teacher`。
其他字段一律 400。

---

## 五、POST /api/followup/review/batch　（仅 owner）

```json
{ "reason": "CLEAN", "action": "CONFIRM", "limit": 200 }
```

```json
{ "code": 0, "data": { "affected": 200, "pendingLeft": 328 } }
```

| 参数 | 说明 |
| --- | --- |
| `reason` | 分组代号；`CLEAN` 表示「无疑点全通过」；`URGENT` 表示临期组 |
| `action` | `CONFIRM` / `REJECT` / `PARK` / `REASSIGN` |
| `teacher` | 仅 `REASSIGN` 时必填 |
| `limit` | 单次最多处理条数，默认 200 |

**幂等**：只处理当前 `PENDING` 的记录，重复调用不会重复处理。

---

## 六、GET /api/followup/tasks/today　（teacher）

```json
{
  "code": 0,
  "data": {
    "date": "2026-11-06",
    "snapshotDate": "2026-10-31",
    "progress": { "done": 3, "total": 20 },
    "items": [
      {
        "id": 123,
        "name": "李菡蕾",
        "mobileMasked": "136****5177",
        "queue": "RED",
        "queueLabel": "临期有余额",
        "lessonsLeft": 40,
        "daysLeft": 7,
        "lastClassDate": "2024-11-20",
        "daysSinceClass": 716,
        "lastFollowDate": "2026-10-28",
        "lastResultText": "未接通",
        "dataUnconfirmed": false
      }
    ]
  }
}
```

- **只返回 `review_status='VERIFIED'` 且 `teacher=<自己>`**（接口层硬过滤）
- `dataUnconfirmed=true` 表示曾已分发但数据后来变了，仍可见但提示「数据待确认」
- **不设每日上限**；分页每页 20 条

---

## 七、GET /api/followup/members/{id}　（teacher）

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "name": "李菡蕾",
    "mobileMasked": "136****5177",
    "dataUnconfirmed": false,
    "stats": {
      "lessonsLeft": 40, "daysLeft": 7,
      "lastClassDate": "2024-11-20", "daysSinceClass": 716,
      "attendCount": 0, "totalPaid": 2400, "signCount": 1, "bought199": true
    },
    "talkScript": "……（原样返回，不截断）",
    "history": [
      { "followDate": "2026-10-28", "staffName": "柒柒", "resultCode": 6, "resultText": "未接通", "note": "下午打一次" }
    ],
    "resultOptions": [
      { "code": 1, "text": "已续费" }, { "code": 2, "text": "有意向" }, { "code": 3, "text": "考虑中" },
      { "code": 4, "text": "没时间" }, { "code": 5, "text": "不续" },   { "code": 6, "text": "未接通" },
      { "code": 7, "text": "要顺延" }, { "code": 8, "text": "已流失" }, { "code": 9, "text": "其他" }
    ]
  }
}
```

权限：teacher 访问非自己名下的 → `404`。

---

## 八、POST /api/followup/followups　（teacher）

```json
{ "memberId": 123, "resultCode": 2, "note": "月底发工资再定", "amount": null }
```

```json
{ "code": 0, "data": { "progress": { "done": 4, "total": 20 } } }
```

- `resultCode` 必须 1–9
- 同一天同一人重复提交 = **覆盖**（`mobile + follow_date` 唯一键）
- 提交后直接返回最新进度

---

## 九、POST /api/followup/followups/{id}/flag　（teacher）

```json
{ "note": "她说上周已经续了 50 次卡" }
```

```json
{ "code": 0, "data": { "reviewStatus": "PENDING" } }
```

效果：状态回 PENDING，`review_reason=TEACHER_FLAGGED`，写 review_log；**该老师仍然看得到这条**，只标「数据待确认」。

---

## 十、GET /api/followup/stats/today

teacher：

```json
{ "code": 0, "data": { "date": "2026-11-06", "done": 3, "total": 20 } }
```

owner 额外返回：

```json
{
  "code": 0,
  "data": {
    "date": "2026-11-06",
    "done": 4, "total": 20,
    "byStaff": [
      { "name": "柒柒", "done": 3, "total": 15 },
      { "name": "三水", "done": 1, "total": 5 }
    ],
    "review": { "pending": 87, "verifiedToday": 24, "teacherFlags": 2 }
  }
}
```

---

## 十一、权限总表

| # | 接口 | owner | teacher |
| --- | --- | --- | --- |
| 1 | `POST /auth/login` | ✅ | ✅ |
| 2 | `GET /followup/review/groups` | ✅ | ❌ 403 |
| 3 | `GET /followup/review/items` | ✅ | ❌ 403 |
| 4 | `POST /followup/review/items/{id}` | ✅ | ❌ 403 |
| 5 | `POST /followup/review/batch` | ✅ | ❌ 403 |
| 6 | `GET /followup/tasks/today` | ✅（看全部） | ✅（只看自己） |
| 7 | `GET /followup/members/{id}` | ✅ | ✅（仅自己名下） |
| 8 | `POST /followup/followups` | ✅ | ✅（仅自己名下） |
| 9 | `POST /followup/followups/{id}/flag` | ✅ | ✅（仅自己名下） |
| 10 | `GET /followup/stats/today` | ✅ | ✅ |
| 11 | `GET /health` | ✅ | ✅ |

> 权限判断**只在一处**（Security 规则 + 一个 `canAccessMember()`），不散落在各 Controller。

---

## 十二、红线

- 不做批量提交（`review/batch` 是服务端遍历，前端不传 ID 数组）
- 不做 GraphQL、不做 webhook、不对外开放 API
- `followup_review_log` 只写不读，不提供任何查询接口
