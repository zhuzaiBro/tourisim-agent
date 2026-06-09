# TourismRAG — 多智能体协作旅游规划系统

一套面向旅游场景的 **多智能体协作智能系统**，支持城市知识库管理、文件/数据库摄入、向量检索、流式对话，以及 **8 专家智能体协同规划行程**。

-   后端：Spring Boot 3 + LangChain4j + MySQL + Milvus + MinIO
-   前端：Vue 3 + Nginx
-   部署：Docker Compose（多容器一键启动）
-   智能体：8 个专家智能体，4 阶段 DAG 并行编排，SSE 实时流式可视化

---

## 1. 项目能力

### 核心功能

-   城市知识库问答（按城市过滤检索）
-   向量化检索（Milvus）
-   多种摄入方式：
    -   内置示例数据（青岛）
    -   从 MySQL `attraction` 表摄入
    -   上传文件摄入（`.md/.txt/.pdf`）
-   流式回复（SSE）
-   会话历史管理
-   管理后台（管理员登录 + 统计 + 城市启停）

### 多智能体协作系统（v2.0 新增）

-   **8 个专家智能体**并行协作规划行程
-   **4 阶段 DAG 编排**：天气+POI → 路线+美食 → 日程+预算 → 叙事+审核
-   **智能体辩论协议**：审核智能体发现问题后发起多智能体投票
-   **SSE 实时可视化**：前端仪表盘展示智能体思考、工具调用、辩论过程
-   **故障自愈**：单个智能体失败自动降级，不影响整体
-   **推荐多智能体模式**：前端默认多 Agent；单 Agent（`/api/agent`）保留为 Legacy 兼容

---

## 2. 多智能体架构概览

```
┌─────────────────────────────────────────────────────────┐
│                     用户请求                              │
└──────────────────────┬──────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────┐
│              编排器 (OrchestratorService)                  │
│         解析依赖 → 构建 DAG → 4 阶段执行                    │
└──────────────────────┬──────────────────────────────────┘
                       ▼
    ┌──────────────────────────────────────────────────┐
    │ 第 1 阶段（并行）                                  │
    │    气象分析专家      景点发现专家                 │
    └──────────┬──────────────────┬───────────────────┘
               ▼                  ▼
    ┌──────────────────────────────────────────────────┐
    │ 第 2 阶段（并行）                                  │
    │    路线优化专家      美食推荐专家                 │
    └──────────┬──────────────────┬───────────────────┘
               ▼                  ▼
    ┌──────────────────────────────────────────────────┐
    │ 第 3 阶段（并行）                                  │
    │    日程编排专家      预算规划专家                 │
    └──────────┬──────────────────┬───────────────────┘
               ▼                  ▼
    ┌──────────────────────────────────────────────────┐
    │ 第 4 阶段（串行）                                  │
    │    旅行叙事作家  →    质量审核专家                │
    │                        ↓ (发现问题时)              │
    │                   ⚖️ 智能体辩论投票                 │
    └──────────────────────────────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────────────┐
│               📋 行程结果 (ItineraryResponse)              │
└─────────────────────────────────────────────────────────┘
```

### 8 个专家智能体

智能体

ID

职责

类型

🌤️ 气象分析专家

`weather-analysis`

获取天气数据，评估户外适宜度

工具型

📍 景点发现专家

`poi-discovery`

搜索景点，按偏好排序，室内/室外标注

工具型

🗺️ 路线优化专家

`route-optimization`

多点路线规划，晴雨双路线

工具型

🍜 美食推荐专家

`food-recommendation`

景点附近餐厅发现，用餐时段匹配

工具型

⏱️ 日程编排专家

`day-scheduling`

构建全天时间槽活动安排

算法型

💰 预算规划专家

`budget-planning`

费用估算，预算优化建议

算法型

✨ 旅行叙事作家

`narrative-generation`

LLM 生成每日叙述和行程总结

LLM 型

🛡️ 质量审核专家

`safety-validation`

交叉验证所有输出，发起辩论

LLM 型

---

## 3. 目录结构

```text
.
├── backend/                           # Spring Boot 后端
│   └── src/main/java/com/tourism/rag/
│       ├── agent/
│       │   ├── multiagent/            # ★ 多智能体核心
│       │   │   ├── core/              # 智能体基类、上下文、事件、注册中心
│       │   │   ├── orchestration/     # DAG 编排引擎
│       │   │   ├── communication/     # 智能体间通信、辩论协议
│       │   │   ├── specialists/       # 8 个专家智能体实现
│       │   │   ├── streaming/         # SSE 流式发布
│       │   │   └── persona/           # 智能体人设库
│       │   └── provider/              # 天气/地图/美食数据提供者
│       ├── config/                    # Spring 配置
│       ├── controller/                # REST 控制器
│       ├── service/                   # 业务服务（含原单智能体）
│       └── dto/multiagent/            # 多智能体 DTO
│   └── src/main/resources/
│       ├── application.yml
│       └── data/qingdao_knowledge.md
├── frontend/                          # Vue 3 前端
│   └── src/
│       ├── components/
│       │   ├── MultiAgentDashboard.vue  # ★ 多智能体仪表盘
│       │   ├── AgentCard.vue            # ★ 智能体状态卡片
│       │   ├── AgentActivityLog.vue     # ★ 活动日志
│       │   ├── DebateMonitor.vue        # ★ 辩论可视化
│       │   ├── StageProgressBar.vue     # ★ 阶段进度条
│       │   └── ...                      # 其他组件
│       ├── api/multiAgent.ts            # ★ 多智能体 API 客户端
│       └── types/multiAgent.ts          # ★ 多智能体类型定义
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 4. 运行环境要求

-   Docker Desktop（Mac/Windows/Linux 均可）
-   Docker Compose v2（随 Docker Desktop 自带）
-   可访问 DashScope（通义千问）API

> 本项目默认 MySQL 映射端口为 `3307`（避免占用本机 `3306`）。

---

## 5. 环境变量配置

### 5.1 复制模板

```bash
cp .env.example .env
```

### 5.2 编辑 `.env`

至少需要配置：

```env
# DashScope API Key（必填）
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx

# MySQL
MYSQL_ROOT_PASSWORD=tourism_root_2024
MYSQL_DATABASE=tourism_rag
MYSQL_USER=tourism
MYSQL_PASSWORD=tourism_pass_2024

# 端口
BACKEND_PORT=8080
FRONTEND_PORT=3000

# 管理员初始化密钥（建议补充）
ADMIN_SETUP_KEY=voyage-admin-init-2024

# ★ 多智能体配置（可选）
MULTI_AGENT_ENABLED=true
MULTI_AGENT_DEBATE_ENABLED=true
MULTI_AGENT_STREAM_TIMEOUT=120000
```

---

## 6. Docker 一键启动

### 6.1 启动前确认 Docker 引擎在线

```bash
docker info
```

### 6.2 构建并启动

```bash
docker compose --env-file .env up -d --build
```

### 6.3 查看状态

```bash
docker compose ps
```

你应看到这些服务：

-   `tourism-mysql`
-   `tourism-etcd`
-   `tourism-minio`
-   `tourism-milvus`
-   `tourism-backend`
-   `tourism-frontend`

访问地址：

-   前端：[http://localhost:3000](http://localhost:3000)
-   后端健康检查：[http://localhost:8080/api/cities](http://localhost:8080/api/cities)
-   多智能体状态：[http://localhost:8080/api/multi-agent/status](http://localhost:8080/api/multi-agent/status)

---

## 7. 首次初始化流程（推荐）

### 7.1 初始化默认城市（青岛）

```bash
curl -X POST http://localhost:8080/api/cities/init
```

### 7.2 触发青岛内置知识库摄入

```bash
curl -X POST http://localhost:8080/api/ingest/qingdao
```

### 7.3 查看后端日志确认摄入

```bash
docker compose logs -f backend
```

---

## 8. 管理后台使用

前端路由：

-   管理员登录页：`/admin/login`
-   管理后台首页：`/admin`

完整地址：[http://localhost:3000/admin/login](http://localhost:3000/admin/login)

首次使用管理员：

1.  打开管理员登录页
2.  切换到"首次初始化管理员"
3.  输入：`setupKey + username + email + password`
4.  `setupKey` 对应 `ADMIN_SETUP_KEY`

普通用户入口：[http://localhost:3000/login](http://localhost:3000/login)

---

## 9. 常用 API（可直接联调）

以下示例都以 `http://localhost:8080` 为后端地址。

### 9.1 认证

注册：

```bash
curl -X POST http://localhost:8080/api/auth/register 
  -H 'Content-Type: application/json' 
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "12345678"
  }'
```

登录：

```bash
curl -X POST http://localhost:8080/api/auth/login 
  -H 'Content-Type: application/json' 
  -d '{
    "email": "test@example.com",
    "password": "12345678"
  }'
```

### 9.2 城市

获取启用城市（公开）：

```bash
curl http://localhost:8080/api/cities
```

### 9.3 对话

普通对话：

```bash
curl -X POST http://localhost:8080/api/chat 
  -H 'Authorization: Bearer <TOKEN>' 
  -H 'Content-Type: application/json' 
  -d '{
    "sessionId":"session-demo-001",
    "cities":["beijing"],
    "question":"北京两日游怎么安排？"
  }'
```

流式对话（SSE）：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream 
  -H 'Authorization: Bearer <TOKEN>' 
  -H 'Content-Type: application/json' 
  -d '{
    "sessionId":"session-demo-002",
    "cities":["beijing"],
    "question":"帮我规划北京亲子三日游",
    "stream": true
  }'
```

### 9.4 行程规划 — Legacy 单智能体（兼容）

> 推荐使用下方 **9.5 多智能体模式**。

```bash
curl -X POST http://localhost:8080/api/agent/itinerary 
  -H 'Authorization: Bearer <TOKEN>' 
  -H 'Content-Type: application/json' 
  -d '{
    "cityCode":"qingdao",
    "startDate":"2026-06-10",
    "endDate":"2026-06-12",
    "preferences":["food","photography"],
    "budget":"medium",
    "transportMode":"transit",
    "adults":2,
    "children":0
  }'
```

### 9.5 ★ 行程规划 — 多智能体模式（v2.0 新增）

**非流式**（一次性返回结果）：

```bash
curl -X POST http://localhost:8080/api/multi-agent/itinerary 
  -H 'Authorization: Bearer <TOKEN>' 
  -H 'Content-Type: application/json' 
  -d '{
    "cityCode":"qingdao",
    "startDate":"2026-06-10",
    "endDate":"2026-06-12",
    "preferences":["food","photography"],
    "budget":"medium",
    "transportMode":"transit"
  }'
```

**流式**（SSE 实时推送智能体活动）：

```bash
curl -N -X POST http://localhost:8080/api/multi-agent/itinerary/stream 
  -H 'Authorization: Bearer <TOKEN>' 
  -H 'Content-Type: application/json' 
  -d '{
    "cityCode":"qingdao",
    "startDate":"2026-06-10",
    "endDate":"2026-06-12",
    "preferences":["food","photography"],
    "budget":"medium",
    "transportMode":"transit"
  }'
```

SSE 事件类型包括：

-   `ORCHESTRATION_STARTED` — 编排开始
-   `STAGE_STARTED` / `STAGE_COMPLETED` — 阶段进度
-   `AGENT_STARTED` / `AGENT_THINKING` / `AGENT_TOOL_CALL` / `AGENT_COMPLETED` — 智能体生命周期
-   `AGENT_FAILED` / `AGENT_FALLBACK` — 智能体故障与降级
-   `DEBATE_INITIATED` / `VOTE_CAST` / `CONSENSUS_REACHED` — 辩论投票
-   `FINAL_RESULT` — 最终行程结果

**查询所有智能体**：

```bash
curl http://localhost:8080/api/multi-agent/agents | jq
```

**查询多智能体系统状态**：

```bash
curl http://localhost:8080/api/multi-agent/status | jq
```

### 9.6 知识摄入

从数据库摄入指定城市：

```bash
curl -X POST http://localhost:8080/api/ingest/db/beijing 
  -H 'Authorization: Bearer <TOKEN>'
```

上传文件摄入：

```bash
curl -X POST http://localhost:8080/api/ingest/upload 
  -H 'Authorization: Bearer <TOKEN>' 
  -F 'file=@/absolute/path/knowledge.md' 
  -F 'cityCode=beijing' 
  -F 'category=knowledge'
```

---

## 10. 知识库摄入说明

当你在系统里新增一个城市（如 `beijing`）后，仅创建了城市元数据，**还需要做一次摄入**，该城市才能被问答检索到。

推荐顺序：

1.  新增城市（管理端）
2.  选择一种摄入方式：
    -   数据库摄入：`POST /api/ingest/db/{cityCode}`
    -   文件摄入：`POST /api/ingest/upload`
3.  观察后端日志确认完成
4.  在问答时选择该城市

---

## 11. 多智能体配置参考

以下配置在 `backend/src/main/resources/application.yml` 中：

```yaml
multi-agent:
  enabled: true                    # 是否启用多智能体
  streaming:
    timeout-ms: 120000             # SSE 流超时时间
  agents:
    weather-analysis:
      timeout-ms: 5000             # 气象智能体超时
      retry-count: 1
    poi-discovery:
      timeout-ms: 8000             # 景点智能体超时
      retry-count: 1
    narrative-generation:
      timeout-ms: 10000            # LLM 叙事智能体超时（较长）
      retry-count: 1
    # ... 其他智能体类似
  debate:
    enabled: true                  # 是否启用智能体辩论
    max-rounds: 2                  # 最大辩论轮次
    consensus-threshold: 0.5       # 共识阈值（>50% 通过）
```

---

## 12. 常见问题排查

### Q1: `Cannot connect to the Docker daemon`

Docker Desktop 未启动。打开 Docker Desktop，等待引擎 ready 后再执行 `docker compose ...`。

### Q2: 前端 `/api/chat` 返回 `502 Bad Gateway`

通常是后端不可用或重启中。

```bash
docker compose ps
docker compose logs -f backend
```

### Q3: `/api/chat` 返回 500

常见原因：

-   `DASHSCOPE_API_KEY` 未配置或不可用
-   Milvus 集合/索引状态异常
-   城市未摄入数据，检索结果为空

### Q4: 多智能体模式不工作

检查：

1.  `curl http://localhost:8080/api/multi-agent/status` 确认 `mode: "multi-agent"`
2.  `curl http://localhost:8080/api/multi-agent/agents` 确认注册了 8 个智能体
3.  后端日志搜索 `[AgentRegistry]` 确认智能体注册成功
4.  `MULTI_AGENT_ENABLED` 环境变量是否为 `true`

### Q5: 智能体全部走 Mock/降级

说明高德地图 API 没有配置或不可用。配置 `MAP_API_KEY` 环境变量即可启用真实 API。

---

## 13. 运维命令速查

启动：

```bash
docker compose --env-file .env up -d --build
```

停止：

```bash
docker compose down
```

查看状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f frontend
```

重建后端：

```bash
docker compose --env-file .env up -d --build backend
```

---

## 14. 本地开发模式

### 后端

要求：JDK 21 + Maven

```bash
cd backend
mvn spring-boot:run
```

### 前端

要求：Node.js 20+

```bash
cd frontend
npm install
npm run dev
```

前端开发地址：[http://localhost:5173](http://localhost:5173)（默认代理 `/api` 到 `http://localhost:8080`）

---

## 15. 安全与生产建议

-   生产环境务必替换：
    -   `JWT_SECRET`
    -   `ADMIN_SETUP_KEY`
    -   MySQL 密码
-   关闭测试日志级别，避免敏感信息泄露
-   为管理员接口补充更严格权限与审计策略
-   对上传文件增加大小限制与内容安全校验

---

## 16. 技术亮点（答辩/演示用）

亮点

说明

**多智能体协作**

8 个专家智能体并行协作，非单一 AI 调用

**DAG 编排引擎**

基于拓扑排序的 4 阶段依赖解析与并行调度

**智能体辩论协议**

多智能体投票机制，质量审核→辩论→共识

**实时流式可视化**

SSE 推送 17 种事件类型，前端仪表盘展示智能体思考过程

**虚拟线程并发**

JDK 21 虚拟线程实现真正的并行智能体执行

**故障自愈**

单智能体超时/失败自动降级到 Mock，不影响整体

**多智能体优先**

前端默认多 Agent 编排；Legacy 单 Agent 端点保留对比测试

**人设驱动**

每个智能体有独立的角色人设、系统提示词、沟通风格

---

## 17. License / 使用说明

本仓库用于技术学习、方案验证与二次开发。若用于商业化，请根据你的实际授权策略补充许可证与版权声明。