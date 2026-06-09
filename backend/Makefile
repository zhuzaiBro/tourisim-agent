# TourismRAG Backend — 本地开发启动
# 要求: JDK 21、Maven；MySQL/Milvus 建议用 make deps 从 docker-compose 拉起

ROOT_DIR    := $(abspath $(dir $(lastword $(MAKEFILE_LIST)))/..)
BACKEND_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ENV_FILE    := $(ROOT_DIR)/.env
COMPOSE     := docker compose --env-file $(ENV_FILE)

MVN ?= mvn
PYTHON ?= python3
JAVA_MIN := 21
XHS_SIGN_PORT ?= 8765

# 与 docker-compose 一致：宿主机 MySQL 映射 3307
MYSQL_HOST_PORT ?= 3307
MYSQL_DATABASE  ?= tourism_rag
MYSQL_USER      ?= tourism
MYSQL_PASSWORD  ?= tourism_pass_2024
BACKEND_PORT    ?= 8080

MYSQL_JDBC_URL  := jdbc:mysql://localhost:$(MYSQL_HOST_PORT)/$(MYSQL_DATABASE)?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai

.PHONY: help run start dev deps deps-down build test package clean check-java health xhs-sign xhs-check

.DEFAULT_GOAL := help

help: ## 显示可用命令
	@echo "TourismRAG Backend"
	@echo ""
	@echo "  make run / start   启动 Spring Boot（加载 $(ENV_FILE)）"
	@echo "  make dev           依赖容器 + 小红书签名(可选) + 后端"
	@echo "  make deps          仅启动 MySQL + Milvus 依赖（docker compose）"
	@echo "  make deps-down     停止依赖容器"
	@echo "  make build         mvn package（跳过测试）"
	@echo "  make test          运行单元测试"
	@echo "  make health        检查 /api/cities 是否可访问"
	@echo ""
	@echo "首次使用: cd $(ROOT_DIR) && cp .env.example .env"

check-java: ## 检查 JDK 版本
	@java -version 2>&1 | head -1
	@java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])' || \
		(echo "错误: 需要 JDK $(JAVA_MIN)+，当前版本不满足" && exit 1)

# 在子 shell 中加载 .env 并导出 Spring 所需变量
define run-with-env
	set -a; \
	[ -f "$(ENV_FILE)" ] && . "$(ENV_FILE)"; \
	set +a; \
	export SPRING_DATASOURCE_URL="$${SPRING_DATASOURCE_URL:-$(MYSQL_JDBC_URL)}"; \
	export SPRING_DATASOURCE_USERNAME="$${SPRING_DATASOURCE_USERNAME:-$(MYSQL_USER)}"; \
	export SPRING_DATASOURCE_PASSWORD="$${SPRING_DATASOURCE_PASSWORD:-$(MYSQL_PASSWORD)}"; \
	export MILVUS_HOST=localhost; \
	export MILVUS_PORT="$${MILVUS_PORT:-19530}"; \
	cd "$(BACKEND_DIR)" && $(1)
endef

run start: check-java ## 本地启动后端 (mvn spring-boot:run)
	@echo ">>> 启动后端 http://localhost:$(BACKEND_PORT)"
	@$(call run-with-env,$(MVN) spring-boot:run)

# dev：依赖 + 可选 XHS 签名后台进程 + Spring Boot（退出时自动停止签名服务）
define dev-with-xhs
	set -e; \
	set -a; \
	[ -f "$(ENV_FILE)" ] && . "$(ENV_FILE)"; \
	set +a; \
	export SPRING_DATASOURCE_URL="$${SPRING_DATASOURCE_URL:-$(MYSQL_JDBC_URL)}"; \
	export SPRING_DATASOURCE_USERNAME="$${SPRING_DATASOURCE_USERNAME:-$(MYSQL_USER)}"; \
	export SPRING_DATASOURCE_PASSWORD="$${SPRING_DATASOURCE_PASSWORD:-$(MYSQL_PASSWORD)}"; \
	export MILVUS_HOST=localhost; \
	export MILVUS_PORT="$${MILVUS_PORT:-19530}"; \
	XHS_PID=""; \
	if [ "$${XHS_ENABLED:-false}" = "true" ]; then \
		$(MAKE) -s xhs-check; \
		if [ "$${#XHS_COOKIE}" -lt 200 ] 2>/dev/null; then \
			echo "警告: XHS_COOKIE 仅 $${#XHS_COOKIE} 字符，可能被截断。请用双引号包裹整段 Cookie" >&2; \
		fi; \
		echo ">>> 后台启动小红书签名服务 http://127.0.0.1:$${XHS_SIGN_PORT:-$(XHS_SIGN_PORT)}/sign"; \
		cd "$(BACKEND_DIR)" && XHS_SIGN_PORT=$${XHS_SIGN_PORT:-$(XHS_SIGN_PORT)} $(PYTHON) scripts/xhs_sign_server.py & \
		XHS_PID=$$!; \
		sleep 0.6; \
	fi; \
	cleanup() { if [ -n "$$XHS_PID" ]; then kill $$XHS_PID 2>/dev/null || true; fi; }; \
	trap cleanup EXIT INT TERM; \
	echo ">>> 启动后端 http://localhost:$(BACKEND_PORT)"; \
	cd "$(BACKEND_DIR)" && $(MVN) spring-boot:run
endef

dev: deps check-java ## 启动依赖 + 小红书签名(可选) + 后端
	@$(call dev-with-xhs)

deps: ## 启动 MySQL / etcd / MinIO / Milvus
	@test -f "$(ENV_FILE)" || (echo "缺少 $(ENV_FILE)，请先: cp .env.example .env" && exit 1)
	@echo ">>> 启动基础设施 (MySQL:$(MYSQL_HOST_PORT), Milvus:19530)"
	@cd "$(ROOT_DIR)" && $(COMPOSE) up -d mysql etcd minio milvus
	@echo ">>> 等待 MySQL 就绪..."
	@cd "$(ROOT_DIR)" && $(COMPOSE) exec -T mysql mysqladmin ping -h localhost -u root -p"$$MYSQL_ROOT_PASSWORD" --silent 2>/dev/null || \
		(echo "提示: 容器刚启动，若后端连库失败可稍等几秒后重试 make run")

deps-down: ## 停止基础设施容器
	@test -f "$(ENV_FILE)" || (echo "缺少 $(ENV_FILE)" && exit 1)
	@cd "$(ROOT_DIR)" && $(COMPOSE) stop mysql etcd minio milvus

build package: check-java ## 编译 jar（跳过测试）
	@cd "$(BACKEND_DIR)" && $(MVN) clean package -DskipTests

test: check-java ## 运行测试
	@cd "$(BACKEND_DIR)" && $(MVN) test

clean: ## 清理构建产物
	@cd "$(BACKEND_DIR)" && $(MVN) clean

health: ## 健康检查
	@curl -sf "http://localhost:$(BACKEND_PORT)/api/cities" >/dev/null && \
		echo "OK  http://localhost:$(BACKEND_PORT)/api/cities" || \
		(echo "后端未就绪: http://localhost:$(BACKEND_PORT)/api/cities" && exit 1)

xhs-check: ## 检查 xhshow 是否已安装（XHS_ENABLED=true 时由 make dev 调用）
	@$(PYTHON) -c "from xhshow import Xhshow" 2>/dev/null || \
		(echo "警告: 未安装 xhshow，小红书签名可能失败。请运行: pip install xhshow" >&2; true)

xhs-sign: xhs-check ## 前台启动小红书签名服务（调试用）
	@cd "$(BACKEND_DIR)" && XHS_SIGN_PORT=$(XHS_SIGN_PORT) $(PYTHON) scripts/xhs_sign_server.py
