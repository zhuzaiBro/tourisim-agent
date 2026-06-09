#!/usr/bin/env bash
# 宝塔 / Linux 手动启动小红书签名服务（开发或临时调试）
set -euo pipefail
cd "$(dirname "$0")/.."
export XHS_SIGN_PORT="${XHS_SIGN_PORT:-8765}"
PYTHON="${PYTHON:-python3}"
if ! "$PYTHON" -c "from xhshow import Xhshow" 2>/dev/null; then
  echo "缺少 xhshow，请先执行: pip install -r scripts/requirements.txt" >&2
  exit 1
fi
exec "$PYTHON" scripts/xhs_sign_server.py
