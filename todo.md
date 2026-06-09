# TourismRAG 改造清单

> 基于当前代码库梳理。目标：从「演示可跑」升级为「可交付、可扩展、可运维」。
>
> 状态标记：`[ ]` 未开始 · `[~]` 进行中 · `[x]` 已完成

---

## 阶段 0：已识别问题（优先验证） ✅ 2026-06-05

- [x] **修复高德 POI URL 编码**
  - **文件**：`backend/src/main/java/com/tourism/rag/agent/provider/GaodeMapProvider.java`
  - **改造**：`UriComponentsBuilder` + UTF-8 编码 `city` / `types`
  - **目标**：配置 `MAP_API_KEY` 后 POI 搜索不再误降级 Mock
  - **验收**：日志 `[Gaode] 获取到 12 个 POI`；`hasRealPoiData=true`；`searchPOI.provider=gaode_api`
  - **回归测试**：`backend/src/test/java/.../GaodeMapProviderTest.java`

- [x] **重建并验证 backend**
  - **Docker 镜像**：`docker compose --env-file .env build backend` 已成功（含修复后 jar）
  - **本地 dev**：`make dev` 下 API 验收通过（2026-06-05）
  - **验收命令**：
    ```bash
    curl -sf http://localhost:8080/api/cities
    curl -sf -X POST http://localhost:8080/api/agent/itinerary \
      -H 'Content-Type: application/json' \
      -d '{"cityCode":"qingdao","startDate":"2026-06-05","endDate":"2026-06-05","preferences":[],"budget":"medium","transportMode":"transit"}'
    # 检查响应：hasRealPoiData=true，toolCallLogs 中 searchPOI.provider=gaode_api
    ```
  - **Docker 运行**：若本机已 `make dev` 占用 8080，需先停本地进程再 `docker compose --env-file .env up -d backend`

---

## 阶段 1：核心能力闭环（P0） ✅ 2026-06-05

### 1.1 RAG 与 Agent 融合

- [x] **行程规划接入本地知识库**
  - **实现**：`RagPoiParser` + `PoiSourceService`；`ItineraryAgentService.callPOI` / `PoiDiscoveryAgent` 统一走 RAG 检索 `category=attraction` 并 enrich
  - **验收**（2026-06-05，`make run` + Milvus localhost + reindex qingdao）：
    - `toolCallLogs`: `searchPOI.provider=gaode_api+rag`，`enrichPOI.provider=rag`
    - 需先 `POST /api/ingest/reindex/qingdao` 写入向量，否则 RAG 解析为 0

- [x] **统一 POI 数据源策略**
  - **实现**：`PoiSourceService`（`agent.poi.fallback-order` / `rag-enabled`）
  - **配置**：`.env.example` → `AGENT_POI_FALLBACK_ORDER=gaode,rag,mock`
  - **验收**：降级 Mock 时 `usedFallback=true` 且日志 `[PoiSource] 降级 Mock`；前端 `FoodRecommendList` 支持 `gaode_api+rag` 徽章

### 1.2 高德工具链补全

- [x] **接入高德路线规划 API**
  - **实现**：`GaodeMapProvider.planRoute()` → walking/driving Directions；`transit` 暂映射 driving
  - **验收**：`planRoute.provider=gaode_api`，`route.dataSource=gaode_api`（10.6km / 43min 青岛样例）
  - **注意**：免费 Key QPS 有限，路段间已加 250ms 间隔；超限仍降级 Mock

- [x] **统一外部 API 请求层**
  - **实现**：`GaodeApiClient`（UTF-8 编码、status 解析、debug 日志）
  - **复用**：`GaodeMapProvider`、`GaodeWeatherProvider`、`GaodeFoodProvider`

- [x] **GaodeFoodProvider 对齐 Map 修复**
  - **实现**：周边搜索统一走 `GaodeApiClient`
  - **验收**：`recommendFood.provider=gaode_api`

### 1.3 知识库摄入与数据一致性

- [x] **青岛摄入改为加载 md 文件**
  - **实现**：`DocumentLoaderHelper` + `IngestionService.buildQingdaoDocuments()` 读 `data/qingdao_knowledge.md`
  - **验收**：`POST /api/ingest/reindex/qingdao` → `chunks=9`

- [x] **向量库管理 API**
  - **实现**：`DELETE /api/ingest/city/{code}`、`POST /api/ingest/reindex/{code}`、`GET /api/ingest/status/{jobId}`
  - **验收**：`status=completed`；演示环境 `SecurityConfig` 已开放 `/api/ingest/**`
  - **本地 dev**：`make run` 强制 `MILVUS_HOST=localhost`（`.env` 中 `milvus` 仅适用于 Docker 内）

---

## 阶段 2：多智能体真实化（P1） ✅ 2026-06-05

### 2.1 辩论与审核闭环

- [x] **编排器接入辩论流程**
  - **实现**：`OrchestratorService.runDebateAndRevise()` 在 Stage 5 后按 `multi-agent.debate.enabled` 调用 `SafetyValidationAgent.runDebate()`
  - **SSE**：`DEBATE_INITIATED` / `DEBATE_ARGUMENT` / `VOTE_CAST` / `CONSENSUS_REACHED`（含 voteTally、revisionInstructions）
  - **响应**：`ItineraryResponse.consensusResult`

- [x] **辩论结果驱动行程修订**
  - **REVISE**：`compressSchedule` + 从 Stage 3 重跑（day-scheduling → budget → narrative → safety）
  - **REJECT**：`reducePoisPerDay` + 从 Stage 2 重跑（route → 下游），最多 1 次
  - **实现**：`DayScheduleAgent` 压缩景点/时长；`RouteOptimizationAgent` 减少每日 POI 数

- [x] **辩论逻辑 LLM 化**
  - **实现**：`DebateSession` 注入 `ChatLanguageModel`，论点/投票基于议题 + POI/天气上下文
  - **修复**：`CrossValidator.determineParticipants` 中文 issue 关键词匹配

### 2.2 合并双轨行程引擎

- [x] **单 Agent 与多 Agent 共享工具层**
  - **实现**：`AgentToolFacade`（getWeather/searchPOI/planRoute/recommendFood）
  - **复用**：`ItineraryAgentService` + 4 个 Specialist Agent 统一走 Facade；`toolCallLogs` 来自 `AgentContext`

- [x] **下线或标记 Legacy 单 Agent 模式**
  - **前端**：`ItineraryView` 默认 `useMultiAgent=true`
  - **后端**：`AgentController` 标注 Legacy；README 推荐 `/api/multi-agent/itinerary`

### 2.3 未接入 Provider 清理

- [x] **和风天气：已移除（方案 B）**
  - 删除 `HefengWeatherProvider`；`WeatherProvider` 文档改为 Gaode + Mock

---

## 阶段 3：RAG 能力增强（P1） ✅ 2026-06-05

- [x] **聊天支持分类检索**
  - **实现**：`ChatRequest.category` + `TourismChatService.retrieveForChat()` → `retrieveWithCategoryFilter`
  - **前端**：聊天侧栏「知识分类」筛选（全部 / 景点 / 美食 / 交通）
  - **验收**：`sources[].category` 与所选分类一致

- [x] **启用 retrieveGlobal（联游场景）**
  - **实现**：`ChatRetrievalHelper.isCrossCityComparison()`；对比类问句走 `retrieveGlobal`；多城默认 `retrieveWithCityFilter`
  - **验收**：「青岛和北京哪个更适合亲子游」触发 global 检索

- [x] **Milvus 失败策略改进**
  - **实现**：`milvus.fail-fast=true`（默认）启动失败；`MilvusHealthIndicator` + `spring-boot-starter-actuator`
  - **验收**：`GET /actuator/health` 含 Milvus 状态；停 Milvus 且 fail-fast 时应用不启动

- [x] **会话记忆持久化**
  - **实现**：`ConversationService.loadChatMemoryMessages()`；`getOrCreateMemory` 从 DB 恢复；记忆存原始问答（非 augmented prompt）
  - **验收**：重启后同一 `sessionId` 多轮上下文连贯（需已登录且消息已持久化）

---

## 阶段 4：安全与生产化（P1）

- [ ] **摄入接口管理员鉴权**
  - **改造**：`SecurityConfig` 将 `/api/ingest/**` 改为 `hasRole("ADMIN")`
  - **文件**：`IngestionController` 注释中的 TODO 落地
  - **目标**：普通用户不能向 Milvus 写入任意文档
  - **验收**：USER 角色调用 ingest 返回 403

- [ ] **城市管理接口权限收紧**
  - **改造**：`POST /api/cities`、`POST /api/cities/init` 限制 `ADMIN`；`GET` 保持公开
  - **目标**：防止任意注册用户刷城市数据

- [ ] **Agent 接口鉴权策略**
  - **改造**：评估公开演示需求；至少对写操作（生成行程）加可选 JWT 或 API Key
  - **目标**：防止接口被滥用刷 DashScope / 高德额度

- [ ] **密钥与 CORS 生产配置**
  - **改造**：
    - `.env.example` 去掉真实 Key 示例；强制 `JWT_SECRET`、`ADMIN_SETUP_KEY`
    - CORS `allowedOriginPatterns` 改为前端域名白名单（`LangChain4jConfig` + `SecurityConfig` 去重）
  - **目标**：符合 README §15 安全建议

- [ ] **上传文件安全**
  - **改造**：`IngestionController` 限制文件大小、扩展名白名单、可选病毒扫描钩子
  - **目标**：防止超大 PDF / 恶意文件打满磁盘

- [ ] **数据库 Schema 管理**
  - **改造**：引入 Flyway；`ddl-auto` 改为 `validate`；补充 `db/migration/V1__*.sql`
  - **目标**：多环境部署表结构一致、可回滚

- [ ] **可观测性**
  - **改造**：Spring Boot Actuator + 健康检查（MySQL、Milvus、DashScope 连通性）
  - **目标**：Docker/K8s 能自动探活与告警

---

## 阶段 5：前端与体验（P2）

- [x] **景点地图对接真实数据**
  - **改造**：新增 `ItineraryAttractionMap.vue`，从 `DayPlan.route.optimizedPois` 绘 marker + polyline；`GET /api/cities/{code}/attractions` 供城市景点查询
  - **路线**：侧边栏展示 `RouteInfo.legs` 分段信息
  - **目标**：地图与 Agent 行程一致，可跨城市
  - **验收**：生成青岛行程后地图 marker 与当日 POI 一致

- [x] **收藏 / 笔记 / 行程本后端化**
  - **改造**：`user_favorites` / `user_notes` / `user_plan_books` 表与 `/api/user/**` CRUD；Pinia store 改调 API
  - **目标**：换设备数据不丢；与 userId 绑定
  - **验收**：登录用户 A 看不到用户 B 的笔记

- [x] **我的行程列表**
  - **改造**：`GET /api/agent/itineraries` 分页列表；`ItineraryHistoryView.vue` + `/itinerary/history`
  - **文件**：`ItineraryRecordRepository`、`ItineraryView.vue`（支持 `?id=` 加载历史）
  - **目标**：用户可找回之前生成的行程

- [x] **数据来源可视化统一**
  - **改造**：`DataSourceBadge.vue` 统一行程页、DayPlanCard、FoodRecommendList、地图路线来源展示
  - **目标**：用户知道哪些是真实数据、哪些是演示兜底

- [x] **移除前端 Mock 城市兜底（或降级为 dev-only）**
  - **改造**：`stores/city.ts` 中 `MOCK_CITIES` 仅在 `import.meta.env.DEV` 生效
  - **目标**：生产环境后端故障时明确报错，而非展示假城市

---

## 阶段 6：工程质量（P2）

- [ ] **单元测试**
  - **优先覆盖**：
    - `RetrievalService` 城市过滤
    - `GaodeMapProvider` URL 构建与响应解析（WireMock）
    - `CrossValidator` 规则
    - `DebateSession` 投票计票
  - **目标**：核心逻辑回归可自动化

- [ ] **集成测试**
  - **改造**：Testcontainers（MySQL + Milvus）或 CI 服务容器；冒烟测试 ingest → chat → itinerary
  - **目标**：PR 合并前自动验证主链路

- [ ] **CI 流水线**
  - **改造**：GitHub Actions — `mvn test`、`npm run build`、`docker compose build`
  - **目标**：协作者提交有基本质量门禁

- [ ] **API 文档**
  - **改造**：OpenAPI/SpringDoc 或完善 README 接口表；补充 multi-agent SSE 事件类型说明
  - **目标**：前后端联调不依赖读源码

---

## 阶段 7：运营与扩展（P3）

- [ ] **多城市开箱能力**
  - **改造**：管理后台「新增城市 → 上传知识库 → 一键 ingest → 启用」向导；Agent 侧 `CITY_ADCODE` 可配置化（DB 或 yaml）
  - **目标**：新增城市无需改 Java 代码

- [ ] **预算与票价真实化**
  - **改造**：RAG 文档结构化字段（`ticket_price`、`avg_cost`）；`BudgetPlanningAgent` 优先读结构化数据
  - **目标**：预算区间基于知识库而非固定档位常数

- [ ] **限流与配额**
  - **改造**：按 userId / IP 限制 chat、itinerary、ingest 调用频率；DashScope 超时熔断
  - **目标**：控制 API 成本，防刷

- [ ] **许可证与合规**
  - **改造**：补充 LICENSE；高德/DashScope 数据使用声明；用户隐私政策占位
  - **目标**：商业化或开源发布合规

---

## 建议执行顺序（Roadmap）

```
第 1 周：阶段 0 + 1.2 高德修复验证 + 1.3 md 摄入
第 2 周：阶段 1.1 RAG-Agent 融合 + 1.2 路线 API
第 3 周：阶段 2.1 辩论闭环 + 2.2 工具层合并
第 4 周：阶段 4 安全 + Flyway + 阶段 6 基础测试
第 5 周起：阶段 5 前端 + 阶段 7 运营能力
```

---

## 完成定义（整体项目）

满足以下条件可视为「可交付 Beta」：

1. 行程 POI 默认来自高德 + RAG  enrichment，Mock 仅作显式降级
2. 路线规划使用高德（或明确标注估算）
3. 多 Agent 辩论能触发并影响行程结果
4. 知识库 ingest / 删除 / 重建有管理员 API
5. `/api/ingest`、城市管理受 ADMIN 保护
6. 核心链路有集成测试；Docker 一键启动文档与行为一致
7. 前端地图与行程数据同源，Mock 有用户可见标识

---

## 参考文件索引

| 模块 | 关键路径 |
|------|----------|
| RAG 检索 | `RetrievalService.java`, `TourismChatService.java` |
| 知识库摄入 | `IngestionService.java`, `IngestionController.java` |
| 单 Agent 行程 | `ItineraryAgentService.java`, `AgentController.java` |
| 多 Agent 编排 | `OrchestratorService.java`, `StageExecutor.java` |
| 外部工具 | `GaodeMapProvider.java`, `GaodeWeatherProvider.java`, `GaodeFoodProvider.java`, `Mock*Provider.java` |
| 辩论审核 | `DebateSession.java`, `CrossValidator.java`, `SafetyValidationAgent.java` |
| 安全 | `SecurityConfig.java`, `AuthService.java` |
| 配置 | `application.yml`, `.env.example` |
| 前端行程 | `ItineraryView.vue`, `MultiAgentDashboard.vue`, `TripPlannerForm.vue` |
| 前端地图 | `AttractionMap.vue`（当前硬编码） |

---

*最后更新：2026-06-05 · 与当前 `main` 分支代码对齐*
