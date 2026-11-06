# 06 · ECS 部署手册

> 版本 v1.0 · 2026-11-06
> 目标：把后端挂到 **`https://www.huohuawd.cn/hearth`**，与同机的小智站点（`/` 和 `/api` → 8091）**完全隔离**。

---

## 零、本地开发（先跑通，再上线）

**本地不需要 nginx 也行，但我们保留了一套**，为了本地和线上路径完全一致。

| 组件 | 本地 | 线上 |
| --- | --- | --- |
| 后端 | `localhost:8080` | nginx → `127.0.0.1:8080` |
| 入口 | `localhost:8081/hearth/` | `https://www.huohuawd.cn/hearth/` |
| 数据库 | 本地 MySQL 8.4，库 `hearth` | ECS MySQL，库 `hearth`（同构，`mysqldump` 直搬） |
| JDK | 24（**编译仍必须 `release=21`**） | OpenJDK 21 |

**本地 nginx 启动必须在真实终端里**（从 GUI 无控制台进程启动会 `0xC0000142`）：

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\local-nginx.ps1 -Action start
```

**开发者工具两处设置**

1. 详情 → 本地设置 → 勾选「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」
2. 真机调试时 `127.0.0.1` 指**手机自己**，必须换成 `http://<电脑局域网IP>:8080`；同一 WiFi，防火墙放行 8080

---

## 一、隔离边界（先记住，别越界）

| 资源 | 小智（现有，不许动） | 我们（Hearth） |
| --- | --- | --- |
| URL 前缀 | `/`（SPA）、`/api` | `/hearth/` |
| 端口 | 8091 | **8080** |
| 进程 | 用户 `xiaozhi` 手工启动的裸 java 进程 | `hearth.service`（systemd） |
| 目录 | `/home/xiaozhi/server` | `/opt/hearth` |
| 数据库 | `xiaozhi`（不动） | **`hearth`**（新库 + 新账号） |
| nginx | `xiaozhi.conf` 里非 `hearth` 标记的全部内容 | 仅 `# >>> hearth` 块 |

**三条红线**：不动 `/api` 的 location、不动 `/` 的 SPA 兜底、不动 MySQL 全局参数。

---

## 二、nginx（插进 `/etc/nginx/conf.d/xiaozhi.conf`）

用标记块包住，重复执行是**替换**不是叠加：

```nginx
# >>> hearth begin（本块由 ops/patch-nginx-hearth.sh 维护，勿手改）
location = /hearth {
    return 301 /hearth/;
}

location /hearth/ {
    proxy_pass         http://127.0.0.1:8080/;   # 尾斜杠会剥掉 /hearth/ 前缀
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_read_timeout 30s;
    client_max_body_size 1m;
}
# <<< hearth end
```

**两个注意点**：

1. **不要用 `^~ /hearth/`** —— `^~` 会屏蔽正则 location（游戏站踩过这个坑）
2. **`proxy_pass` 末尾必须有 `/`**，否则路径会带着前缀转给 8080，路由全错

改完必须：

```bash
nginx -t && nginx -s reload
sleep 1   # reload 立即返回，旧 worker 还在退场；不等 1 秒去 curl 会拿到旧结果
```

---

## 三、systemd（`/etc/systemd/system/hearth.service`）

```ini
[Unit]
Description=Hearth Platform API (火花私域)
After=network.target mysqld.service
Wants=mysqld.service

[Service]
Type=simple
User=hearth
Group=hearth
WorkingDirectory=/opt/hearth
EnvironmentFile=/opt/hearth/db.env
EnvironmentFile=/opt/hearth/app.env

ExecStart=/usr/bin/java \
  -Xms96m -Xmx320m \
  -XX:MaxMetaspaceSize=128m \
  -XX:+ExitOnOutOfMemoryError \
  -Duser.timezone=Asia/Shanghai \
  -Dfile.encoding=UTF-8 \
  -jar /opt/hearth/app.jar \
  --spring.profiles.active=prod

Restart=always
RestartSec=5

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ReadWritePaths=/opt/hearth

[Install]
WantedBy=multi-user.target
```

日志走 journald：`journalctl -u hearth -f -n 200`

---

## 四、MySQL（新库新账号）

```sql
CREATE DATABASE hearth DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'hearth'@'localhost' IDENTIFIED BY '<强密码>';
GRANT ALL PRIVILEGES ON hearth.* TO 'hearth'@'localhost';
FLUSH PRIVILEGES;
```

然后 `mysql -u hearth -p hearth < db/cloud.sql` 建表。

**账号只用 `@localhost`**，不开外网访问。

---

## 五、首次上线步骤

```bash
# 1. 服务器准备（一次）
useradd -r -s /sbin/nologin hearth
mkdir -p /opt/hearth && chown hearth:hearth /opt/hearth

# 2. 本机打包
mvn -q clean package -DskipTests

# 3. 上传
scp -i ~/.ssh/aliyun_game_ed25519 target/hearth-*.jar root@101.200.14.51:/opt/hearth/app.jar

# 4. 启服务
chown hearth:hearth /opt/hearth/app.jar
systemctl daemon-reload
systemctl enable --now hearth
systemctl status hearth --no-pager

# 5. 服务自检（服务器上）
curl -s http://127.0.0.1:8080/api/health

# 6. 走 nginx 自检
curl -s https://www.huohuawd.cn/hearth/api/health
```

---

## 六、回滚

```bash
ssh root@101.200.14.51 '
  cp /opt/hearth/app.jar.prev /opt/hearth/app.jar
  systemctl restart hearth
  sleep 8
  systemctl is-active hearth
'
```

回滚后必须验证小智没受影响：

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://www.huohuawd.cn/       # 期望 200
curl -s -o /dev/null -w '%{http_code}\n' https://www.huohuawd.cn/api    # 期望 401
```

---

## 七、日常发布

```bash
mvn -q clean package -DskipTests
scp -i ~/.ssh/aliyun_game_ed25519 target/hearth-*.jar root@101.200.14.51:/opt/hearth/app.jar.new

ssh root@101.200.14.51 '
  chown hearth:hearth /opt/hearth/app.jar.new
  mv /opt/hearth/app.jar.new /opt/hearth/app.jar
  systemctl restart hearth
  sleep 8
  systemctl is-active hearth
  curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/api/health
'
```

（P0 之后由 `ops/deploy.ps1` 一条命令完成）

---

## 八、内存观察

```bash
free -m
ps -eo pid,comm,rss --sort=-rss | head -5
systemctl status hearth --no-pager | head -5
```

| 信号 | 含义 | 处置 |
| --- | --- | --- |
| available < 300 MB | 快 OOM | 先把 `-Xmx` 降到 256m 重启 |
| hearth RSS > 700 MB | 堆外泄漏 | 查连接池、线程池上限 |
| 小智 RSS 上升 | 我们可能把它挤到 swap | 立刻降 C1 堆或临时停服 |

### 8.1 应急开关：停掉小智（先读完再用）

实测：占用 8091 的 java **不是 systemd 服务**，是用户 `xiaozhi` 手工起的裸进程。

| 动作 | 命令 | 注意 |
| --- | --- | --- |
| 停 | `pkill -u xiaozhi -f xiaozhi.server-2.8.17.jar` | **不会自动重启** |
| 恢复 | `su - xiaozhi -c 'cd /home/xiaozhi/server && nohup java -jar xiaozhi.server-2.8.17.jar > server.log 2>&1 &'` | 恢复靠手工 |
| 验证 | `ss -lntp \| grep 8091` | 有监听即恢复 |

**结论：不要主动停它。** 只在已出现 OOM 前兆时才按。

---

## 九、小程序后台（一次性配置）

微信公众平台 → 开发管理 → 开发设置 → 服务器域名：

| 类型 | 填 |
| --- | --- |
| request 合法域名 | `https://www.huohuawd.cn` |

- 域名必须已备案（✅）、HTTPS（✅）、**不能带端口**、不支持 IP
- ⚠️ 每月修改次数有限，配之前先 `curl` 确认接口通
- 只有体验版/正式版真机才校验；开发者工具勾选后不校验
