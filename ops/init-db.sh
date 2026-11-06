#!/usr/bin/env bash
# 初始化 hearth 库：建库、建账号、建表。
#
# 数据库密码由本脚本随机生成，只写进服务器 /opt/hearth/db.env (600)，
# 不打印、不进聊天记录、不进 git。
#
# 用法（在服务器上）：
#   bash init-db.sh /tmp/cloud.sql
#
# 账号名固定 hearth，在 init-db.sh 内部定义，与库名保持一致。

set -euo pipefail

DB=hearth
DBUSER=hearth
ENV_DIR=/opt/hearth
ENV_FILE=$ENV_DIR/db.env
SQL_FILE="${1:-/tmp/cloud.sql}"

if [ ! -f "$SQL_FILE" ]; then
  echo "FATAL: 找不到 SQL 文件 $SQL_FILE" >&2
  echo "先执行：scp db/cloud.sql root@<服务器>:/tmp/cloud.sql" >&2
  exit 2
fi

# ---- 1. 取得 root 凭据 ----
MYSQL_ROOT=(mysql)
if [ -f /root/.my.cnf ]; then
  echo "→ 使用 /root/.my.cnf 连接 MySQL"
elif [ -n "${MYSQL_ROOT_PWD:-}" ]; then
  MYSQL_ROOT=(mysql -uroot -p"$MYSQL_ROOT_PWD")
else
  read -rsp "MySQL root 密码（输入不回显）: " RPWD; echo
  MYSQL_ROOT=(mysql -uroot -p"$RPWD")
fi

if ! "${MYSQL_ROOT[@]}" -e "SELECT 1" >/dev/null 2>&1; then
  echo "FATAL: root 连接失败，密码不对或权限不足" >&3
  exit 3
fi
echo "→ root 连接成功"

# ---- 2. 生成或复用数据库密码 ----
if [ -f "$ENV_FILE" ]; then
  echo "→ 已存在 $ENV_FILE，复用其中密码（不重置）"
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  DBPASS="${DB_PASSWORD:?db.env 里缺少 DB_PASSWORD}"
else
  DBPASS=$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | cut -c1-24)
  echo "→ 已生成新密码（24 位，不回显）"
fi

# ---- 3. 建库建账号 ----
"${MYSQL_ROOT[@]}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB}\` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${DBUSER}'@'localhost' IDENTIFIED BY '${DBPASS}';
ALTER USER '${DBUSER}'@'localhost' IDENTIFIED BY '${DBPASS}';
GRANT ALL PRIVILEGES ON \`${DB}\`.* TO '${DBUSER}'@'localhost';
FLUSH PRIVILEGES;
SQL
echo "→ 库 ${DB} 与账号 ${DBUSER}@localhost 就绪"

# ---- 4. 落盘凭据 ----
mkdir -p "$ENV_DIR"
umask 077
cat > "$ENV_FILE" <<ENV
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=${DB}
DB_USER=${DBUSER}
DB_PASSWORD=${DBPASS}
ENV
chmod 600 "$ENV_FILE"
echo "→ 凭据写入 $ENV_FILE（权限 600，内容不回显）"

# ---- 5. 建表 ----
mysql -u"$DBUSER" -p"$DBPASS" -h 127.0.0.1 "$DB" < "$SQL_FILE" 2>/dev/null
echo "→ 建表完成"

# ---- 6. 自检 ----
echo "== 表清单 =="
mysql -u"$DBUSER" -p"$DBPASS" -h 127.0.0.1 "$DB" -e "SHOW TABLES;" 2>/dev/null
echo "== 账号可见范围（不应看到其他库）=="
mysql -u"$DBUSER" -p"$DBPASS" -h 127.0.0.1 -e "SHOW DATABASES;" 2>/dev/null
