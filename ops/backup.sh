#!/usr/bin/env bash
# 每日备份（在服务器上执行，建议加 cron）
#
# 重要：**数据库账号是 hearth，不是 root**。
#       hearth 账号对 hearth.* 有 ALL PRIVILEGES，足够 mysqldump。
#       OS 上以 root 运行只是为了能写 /var/backups/hearth。
#
# 用法：
#   bash backup.sh
#
# 加到 cron（每天 03:10）：
#   10 3 * * * /usr/bin/bash /opt/hearth/ops/backup.sh >> /var/log/hearth-backup.log 2>&1

set -euo pipefail

ENV_FILE=/opt/hearth/db.env
BACKUP_DIR=/var/backups/hearth
KEEP_DAYS=7

if [ ! -f "$ENV_FILE" ]; then
  echo "FATAL: 找不到 $ENV_FILE" >&2
  exit 2
fi

# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a

mkdir -p "$BACKUP_DIR"
TS=$(date +%F-%H%M)
OUT="$BACKUP_DIR/hearth-$TS.sql.gz"

export MYSQL_PWD="$DB_PASSWORD"

# --no-tablespaces：不加会报 "需要 PROCESS 权限"，虽然不影响导出，但日志会脏
# --single-transaction：InnoDB 一致性快照，不锁表
mysqldump \
  -u"$DB_USER" -h"$DB_HOST" -P"$DB_PORT" \
  --single-transaction \
  --no-tablespaces \
  --triggers \
  --skip-routines \
  --skip-events \
  --default-character-set=utf8mb4 \
  "$DB_NAME" | gzip > "$OUT"

unset MYSQL_PWD

# 只保留最近 N 天
find "$BACKUP_DIR" -name 'hearth-*.sql.gz' -mtime +"$KEEP_DAYS" -delete

echo "备份完成: $OUT"
ls -lh "$OUT"
echo "当前保留："
ls -1 "$BACKUP_DIR" | tail -10
