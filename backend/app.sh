#!/usr/bin/env bash
# TourismRAG 后端生产启动脚本（宝塔 / Linux，JDK 21）
# 用法: ./app.sh {start|stop|restart|redeploy|status}

set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$APP_HOME/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

RUN_DIR="$APP_HOME/run"
LOG_DIR="$APP_HOME/logs"
APP_PID_FILE="$RUN_DIR/app.pid"
XHS_PID_FILE="$RUN_DIR/xhs-sign.pid"
APP_LOG="$LOG_DIR/app.log"
XHS_LOG="$LOG_DIR/xhs-sign.log"

JAVA_MIN=21
# 宝塔 JDK 21 默认路径（.env 中 JAVA_BIN 可覆盖）
BT_JAVA_DEFAULT="/www/server/java/jdk-21.0.2/bin/java"
BACKEND_PORT="${BACKEND_PORT:-8080}"
XHS_SIGN_PORT="${XHS_SIGN_PORT:-8765}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"
MVN="${MVN:-mvn}"
PYTHON="${PYTHON:-python3}"

mkdir -p "$RUN_DIR" "$LOG_DIR"

die() { echo "错误: $*" >&2; exit 1; }

usage() {
  cat <<EOF
TourismRAG 后端管理脚本

用法: $0 {start|stop|restart|redeploy|status}

  start     启动 Java 后端（XHS_ENABLED=true 时同时启动签名服务）
  stop      停止后端与签名服务
  restart   先 stop 再 start
  redeploy  mvn 打包后 restart（需已安装 Maven）
  status    查看运行状态

环境:
  .env 文件默认: $ENV_FILE
  可在 .env 中设置 JAVA_BIN、JAVA_OPTS、BACKEND_PORT、XHS_SIGN_PORT、MVN、PYTHON
EOF
}

load_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  else
    echo "警告: 未找到 $ENV_FILE，将使用默认配置" >&2
  fi
  BACKEND_PORT="${BACKEND_PORT:-8080}"
  XHS_SIGN_PORT="${XHS_SIGN_PORT:-8765}"
  JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"
}

resolve_java() {
  local java_bin
  if [[ -n "${JAVA_BIN:-}" ]]; then
    java_bin="$JAVA_BIN"
  elif [[ -x "$BT_JAVA_DEFAULT" ]]; then
    java_bin="$BT_JAVA_DEFAULT"
  else
    java_bin="java"
  fi
  if [[ ! -x "$java_bin" ]] && ! command -v "$java_bin" >/dev/null 2>&1; then
    die "找不到 Java: $java_bin（可在 .env 设置 JAVA_BIN=$BT_JAVA_DEFAULT）"
  fi
  "$java_bin" -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])' \
    || die "需要 JDK $JAVA_MIN+，当前: $("$java_bin" -version 2>&1 | head -1)"
  echo "$java_bin"
}

export_java_home() {
  local java_bin="$1"
  export JAVA_HOME="${JAVA_HOME:-$(cd "$(dirname "$java_bin")/.." && pwd)}"
  export PATH="$JAVA_HOME/bin:$PATH"
}

find_jar() {
  local jar
  jar="$(ls -t "$APP_HOME"/target/tourism-rag-*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
  [[ -n "$jar" && -f "$jar" ]] || die "未找到 jar，请先执行: $0 redeploy 或 mvn -f $APP_HOME/pom.xml clean package -DskipTests"
  echo "$jar"
}

is_running() {
  local pid_file="$1"
  local pid
  [[ -f "$pid_file" ]] || return 1
  pid="$(cat "$pid_file")"
  [[ -n "$pid" ]] || return 1
  kill -0 "$pid" 2>/dev/null
}

stop_pid_file() {
  local name="$1"
  local pid_file="$2"
  if ! is_running "$pid_file"; then
    rm -f "$pid_file"
    echo ">>> $name 未在运行"
    return 0
  fi
  local pid
  pid="$(cat "$pid_file")"
  echo ">>> 停止 $name (pid=$pid)"
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo ">>> $name 未响应，强制结束"
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
}

start_xhs_sign() {
  if [[ "${XHS_ENABLED:-false}" != "true" ]]; then
    return 0
  fi
  if is_running "$XHS_PID_FILE"; then
    echo ">>> 小红书签名服务已在运行 (pid=$(cat "$XHS_PID_FILE"))"
    return 0
  fi
  if ! "$PYTHON" -c "from xhshow import Xhshow" 2>/dev/null; then
    die "XHS_ENABLED=true 但未安装 xhshow，请执行: pip install -r $APP_HOME/scripts/requirements.txt"
  fi
  echo ">>> 启动小红书签名服务 http://127.0.0.1:$XHS_SIGN_PORT/sign"
  cd "$APP_HOME"
  nohup env XHS_SIGN_PORT="$XHS_SIGN_PORT" "$PYTHON" scripts/xhs_sign_server.py >>"$XHS_LOG" 2>&1 &
  echo $! >"$XHS_PID_FILE"
  sleep 0.8
  is_running "$XHS_PID_FILE" || die "签名服务启动失败，查看日志: $XHS_LOG"
}

start_app() {
  load_env
  local java_bin jar
  java_bin="$(resolve_java)"

  if is_running "$APP_PID_FILE"; then
    echo ">>> 后端已在运行 (pid=$(cat "$APP_PID_FILE"))"
    return 0
  fi

  start_xhs_sign

  jar="$(find_jar)"
  echo ">>> 启动后端 $jar"
  echo ">>> 端口 http://127.0.0.1:$BACKEND_PORT"

  cd "$APP_HOME"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://localhost:3306/tourism_rag?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-tourism}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-tourism_pass_2024}"
  export MILVUS_HOST="${MILVUS_HOST:-localhost}"
  export MILVUS_PORT="${MILVUS_PORT:-19530}"
  export SERVER_PORT="$BACKEND_PORT"

  nohup "$java_bin" $JAVA_OPTS -jar "$jar" >>"$APP_LOG" 2>&1 &
  echo $! >"$APP_PID_FILE"

  for _ in $(seq 1 60); do
    if curl -sf "http://127.0.0.1:$BACKEND_PORT/api/cities" >/dev/null 2>&1; then
      echo ">>> 后端启动成功 (pid=$(cat "$APP_PID_FILE"))"
      return 0
    fi
    is_running "$APP_PID_FILE" || die "后端进程已退出，查看日志: $APP_LOG"
    sleep 2
  done

  echo ">>> 后端进程已启动，健康检查超时，请查看日志: $APP_LOG"
}

stop_app() {
  stop_pid_file "Java 后端" "$APP_PID_FILE"
  stop_pid_file "小红书签名服务" "$XHS_PID_FILE"
}

status_app() {
  load_env
  echo "环境文件: $ENV_FILE"
  echo "工作目录: $APP_HOME"
  echo "后端端口: $BACKEND_PORT"
  if is_running "$APP_PID_FILE"; then
    echo "Java 后端: 运行中 (pid=$(cat "$APP_PID_FILE"))"
  else
    echo "Java 后端: 已停止"
  fi
  if [[ "${XHS_ENABLED:-false}" == "true" ]]; then
    if is_running "$XHS_PID_FILE"; then
      echo "XHS 签名: 运行中 (pid=$(cat "$XHS_PID_FILE"), port=$XHS_SIGN_PORT)"
    else
      echo "XHS 签名: 已停止"
    fi
  else
    echo "XHS 签名: 未启用 (XHS_ENABLED=false)"
  fi
  if curl -sf "http://127.0.0.1:$BACKEND_PORT/api/cities" >/dev/null 2>&1; then
    echo "健康检查: OK  /api/cities"
  else
    echo "健康检查: 未就绪"
  fi
}

redeploy_app() {
  load_env
  local java_bin
  java_bin="$(resolve_java)"
  export_java_home "$java_bin"
  command -v "$MVN" >/dev/null 2>&1 || die "未找到 Maven ($MVN)，请先安装或在 .env 设置 MVN 路径"

  echo ">>> 编译打包 (跳过测试, JAVA_HOME=$JAVA_HOME)..."
  cd "$APP_HOME"
  "$MVN" clean package -DskipTests

  local jar
  jar="$(find_jar)"
  echo ">>> 打包完成: $jar"
  stop_app
  start_app
}

cmd="${1:-}"
case "$cmd" in
  start) start_app ;;
  stop) stop_app ;;
  restart) stop_app; start_app ;;
  redeploy) redeploy_app ;;
  status) status_app ;;
  -h|--help|help) usage ;;
  *)
    usage
    exit 1
    ;;
esac
