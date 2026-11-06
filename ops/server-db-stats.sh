#!/usr/bin/env bash
# 输出服务器上 hearth 库的表/视图与行数，供本地恢复演练做参照。
#
# 输出格式（每行一条，便于机器解析）：
#   T|<表名>|<行数>
#   V|<视图名>
#
# 用法：在服务器上执行  bash server-db-stats.sh

set -euo pipefail

ENV_FILE=/opt/hearth/db.env
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
export MYSQL_PWD="$DB_PASSWORD"

DB="$DB_NAME"

TABLES=$(mysql -u"$DB_USER" -h"$DB_HOST" -N -e \
  "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME")

for t in $TABLES; do
  c=$(mysql -u"$DB_USER" -h"$DB_HOST" -N -D "$DB" -e "SELECT COUNT(*) FROM $t")
  echo "T|$t|$c"
done

VIEWS=$(mysql -u"$DB_USER" -h"$DB_HOST" -N -e \
  "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB' AND TABLE_TYPE='VIEW' ORDER BY TABLE_NAME")

for v in $VIEWS; do
  echo "V|$v"
done

unset MYSQL_PWD
