#!/usr/bin/env bash
# 在服务器上初始化 hearth 库与账号。
#
# 用法（密码只经 stdin 进入内存）：
#   echo "$ROOT_PASSWORD" | bash init-server-db.sh
#
# 安全约定：
#   - root 密码不写进任何文件、不出现在命令行参数里（ps 看不到）
#   - 用完立即 unset，进程退出即消失
#   - 应用账号密码在服务器本地随机生成，不经过网络传输
#   - 只授予 hearth.* 权限，不给全局权限

set -euo pipefail

read -r ROOTPW
# PowerShell 管道会把行尾变成 CRLF，read -r 之后会残留 \r，必须清掉
ROOTPW=$(printf '%s' "$ROOTPW" | tr -d '\r\n')
if [ -z "${ROOTPW:-}" ]; then
  echo "FATAL: 未收到 root 密码（stdin 为空）" >&2
  exit 2
fi
export MYSQL_PWD="$ROOTPW"

echo "== 只读检查（动之前长什么样）=="
printf 'general_log / log_output: '
mysql -uroot -N -e "SELECT @@general_log, @@log_output;"
echo "现有库："
mysql -uroot -N -e "SHOW DATABASES;"
echo "hearth 库是否已存在："
mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='hearth';"

echo
echo "== 建库 =="
mysql -uroot -e "CREATE DATABASE IF NOT EXISTS hearth DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

NEWPW=$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-28)
mysql -uroot -e "CREATE USER IF NOT EXISTS 'hearth'@'localhost' IDENTIFIED BY '${NEWPW}';"
mysql -uroot -e "ALTER USER 'hearth'@'localhost' IDENTIFIED BY '${NEWPW}';"
mysql -uroot -e "GRANT ALL PRIVILEGES ON \`hearth\`.* TO 'hearth'@'localhost';"
mysql -uroot -e "FLUSH PRIVILEGES;"

unset MYSQL_PWD
unset ROOTPW
echo "库与账号就绪；root 密码已从内存清除"

echo
echo "== 用 hearth 账号自检（这是「不能用 root 操作生产库」的实证）=="
export MYSQL_PWD="$NEWPW"
echo "权限："
mysql -uhearth -h 127.0.0.1 -N -e "SHOW GRANTS FOR CURRENT_USER();"
echo "可见的库（应该只有 hearth 与 information_schema）："
mysql -uhearth -h 127.0.0.1 -N -e "SHOW DATABASES;"

echo
echo "== 写 /opt/hearth/db.env（600，仅 hearth 可读）=="
umask 077
cat > /opt/hearth/db.env <<EOF
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=hearth
DB_USER=hearth
DB_PASSWORD=${NEWPW}
EOF
chown hearth:hearth /opt/hearth/db.env
chmod 600 /opt/hearth/db.env
ls -l /opt/hearth/db.env
unset MYSQL_PWD

echo
echo "DONE"
