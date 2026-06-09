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
APP_LOG="$LOG_DIR/app.log"

JAVA_MIN=21
# 宝塔 JDK 21 默认路径（.env 中 JAVA_BIN 可覆盖）
BT_JAVA_DEFAULT="/www/server/java/jdk-21.0.2/bin/java"
BACKEND_PORT="${BACKEND_PORT:-8080}"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"

mkdir -p "$RUN_DIR" "$LOG_DIR"

die() { echo "错误: $*" >&2; exit 1; }

usage() {
  cat <<EOF
TourismRAG 后端管理脚本

用法: $0 {start|stop|restart|redeploy|status}

  start     启动 Java 后端（小红书签名服务请独立管理，本脚本不启停）
  stop      停止 Java 后端
  restart   先 stop 再 start
  redeploy  打包并 restart；无 Maven 时可设 DEPLOY_SKIP_BUILD=true 仅重启 jar
  status    查看运行状态

环境:
  .env 文件默认: $ENV_FILE
  可在 .env 中设置 JAVA_BIN、JAVA_OPTS、BACKEND_PORT、MVN、DEPLOY_SKIP_BUILD
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

resolve_mvn() {
  local candidate
  if [[ -n "${MVN:-}" ]]; then
    if [[ -x "$MVN" ]] || command -v "$MVN" >/dev/null 2>&1; then
      echo "$MVN"
      return 0
    fi
    die "MVN 指向的路径不可用: $MVN"
  fi
  local candidates=(
    /www/server/maven/bin/mvn
    /www/server/apache-maven/bin/mvn
    /usr/local/maven/bin/mvn
    /opt/maven/bin/mvn
    mvn
  )
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate" ]] || command -v "$candidate" >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

jar_exists() {
  local jar
  jar="$(ls -t "$APP_HOME"/target/tourism-rag-*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
  [[ -n "$jar" && -f "$jar" ]]
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

start_app() {
  load_env
  local java_bin jar
  java_bin="$(resolve_java)"

  if is_running "$APP_PID_FILE"; then
    echo ">>> 后端已在运行 (pid=$(cat "$APP_PID_FILE"))"
    return 0
  fi

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
  echo "XHS 签名: 独立管理（Supervisor 等），本脚本不启停"
  if [[ -n "${XHS_SIGN_URL:-}" ]]; then
    echo "XHS_SIGN_URL: $XHS_SIGN_URL"
  fi
  if curl -sf "http://127.0.0.1:$BACKEND_PORT/api/cities" >/dev/null 2>&1; then
    echo "健康检查: OK  /api/cities"
  else
    echo "健康检查: 未就绪"
  fi
}

redeploy_app() {
  load_env
  local java_bin mvn_bin jar
  java_bin="$(resolve_java)"
  export_java_home "$java_bin"

  if mvn_bin="$(resolve_mvn 2>/dev/null)"; then
    echo ">>> 编译打包 (跳过测试, JAVA_HOME=$JAVA_HOME, MVN=$mvn_bin)..."
    cd "$APP_HOME"
    "$mvn_bin" clean package -DskipTests
    jar="$(find_jar)"
    echo ">>> 打包完成: $jar"
  elif [[ "${DEPLOY_SKIP_BUILD:-false}" == "true" ]]; then
    jar_exists || die "DEPLOY_SKIP_BUILD=true 但未找到 target/tourism-rag-*.jar，请先在本地打包并上传"
    jar="$(find_jar)"
    echo ">>> 跳过编译 (DEPLOY_SKIP_BUILD=true)，使用已有 jar: $jar"
  else
    die "$(cat <<EOF
未找到 Maven。宝塔服务器通常不预装 Maven，可选方案:

  方案 A（推荐）本地打包后上传 jar，在 .env 增加:
    DEPLOY_SKIP_BUILD=true
  然后执行: $0 redeploy  或  $0 restart

  方案 B 服务器安装 Maven 后在 .env 设置:
    MVN=/path/to/mvn/bin/mvn

  方案 C 仅重启已有 jar（不上传新包）:
    $0 restart
EOF
)"
  fi

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
