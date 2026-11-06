#!/usr/bin/env bash
# 给 xiaozhi.conf 插入 /hearth/ location
# 约定与 工具/发布/deploy.ps1 的 game-deploy 一致：标记块 + 幂等替换 + 失败自动回滚
#
# 幂等：
#   1. hearth 块已存在 → 整体替换，不叠加
#   2. 存在旧命名的 followup / spark 块 → 先删掉（改名遗留）

set -euo pipefail

CONF=/etc/nginx/conf.d/xiaozhi.conf
TS=$(date +%Y%m%d-%H%M%S)
BAK="${CONF}.bak-hearth-${TS}"
BEGIN='# >>> hearth begin'
END='# <<< hearth end'
ANCHOR='# <<< game-deploy end'

if [ ! -f "$CONF" ]; then
  echo "FATAL: 找不到 $CONF" >&2
  exit 2
fi

cp -a "$CONF" "$BAK"
echo "backup: $BAK"

python3 - "$CONF" "$BEGIN" "$END" "$ANCHOR" <<'PY'
import sys, re

conf, begin, end, anchor = sys.argv[1:5]

block = """# >>> hearth begin（由 ops/patch-nginx-hearth.sh 维护，重复执行整体替换，勿手改）
location = /hearth {
    return 301 /hearth/;
}

location /hearth/ {
    proxy_pass         http://127.0.0.1:8080/;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_read_timeout 30s;
    client_max_body_size 1m;
}
# <<< hearth end"""

with open(conf, encoding='utf-8') as f:
    text = f.read()

# ① 清理改名遗留块（followup / spark）
for legacy_name in ('followup', 'spark'):
    legacy = re.compile(r'\n*# >>> ' + legacy_name + r' begin.*?# <<< ' + legacy_name + r' end\n?', re.S)
    if legacy.search(text):
        text = legacy.sub('\n', text, count=1)
        print('legacy block removed: %s' % legacy_name)

# ② 插入或替换 hearth 块
pattern = re.compile(re.escape(begin) + r'.*?' + re.escape(end), re.S)

if pattern.search(text):
    text = pattern.sub(block, text, count=1)
    mode = 'replaced'
else:
    if anchor not in text:
        print('FATAL: 找不到锚点 %r' % anchor, file=sys.stderr)
        sys.exit(2)
    text = text.replace(anchor, anchor + '\n\n' + block, 1)
    mode = 'inserted'

with open(conf, 'w', encoding='utf-8') as f:
    f.write(text)

print('block: %s' % mode)
PY

echo "== 相关行 =="
grep -n -E 'hearth|followup|spark|game-deploy' "$CONF" || true

echo "== 残留旧命名（应为空）=="
if grep -qE 'followup|spark' "$CONF"; then
  echo "WARN: 仍有旧命名，请人工确认"
else
  echo "OK: 无旧命名残留"
fi

echo "== nginx -t =="
if nginx -t 2>&1; then
  nginx -s reload
  sleep 1   # reload 返回时旧 worker 还在退场，不等 1 秒去 curl 会拿到旧结果
  echo "RELOADED OK"
else
  echo "NGINX TEST FAILED — 自动回滚"
  cp -a "$BAK" "$CONF"
  nginx -t 2>&1 || true
  exit 1
fi

echo "== 验证（/hearth/ 返回 502 是预期的：8080 还没有服务）=="
for u in \
  "https://www.huohuawd.cn/" \
  "https://www.huohuawd.cn/api" \
  "https://www.huohuawd.cn/game/" \
  "https://www.huohuawd.cn/hearth/" \
  "https://www.huohuawd.cn/hearth"
do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$u" || echo ERR)
  printf '%-4s %s\n' "$code" "$u"
done

echo "== 期望：/ = 200 · /api = 401 · /game/ = 200 · /hearth/ = 502 · /hearth = 301 =="
