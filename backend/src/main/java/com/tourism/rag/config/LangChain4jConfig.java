package com.tourism.rag.config;

import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * LangChain4j 核心配置类
 *
 * Bean 注册顺序：
 * 1. EmbeddingModel（Qwen text-embedding-v3）
 * 2. EmbeddingStore（Milvus）
 * 3. ChatLanguageModel（Qwen-Max）
 * 4. StreamingChatLanguageModel（Qwen-Max streaming）
 * 5. EmbeddingStoreContentRetriever（默认检索器，无城市过滤）
 *    → 带城市过滤的检索器在 RetrievalService 动态构建
 *
 * ⚠️ 注意事项：
 * - Milvus collection 维度必须与 EmbeddingModel 输出维度一致（1024）
 * - 首次启动时 Milvus 会自动创建 collection，无需手动建表
 * - 切换 LLM：修改 application.yml 中的 langchain4j.dashscope.chat-model.model-name
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    // ---- DashScope 配置 ----
    @Value("${langchain4j.dashscope.api-key}")
    private String dashscopeApiKey;

    @Value("${langchain4j.dashscope.chat-model.model-name:qwen-max}")
    private String chatModelName;

    @Value("${langchain4j.dashscope.chat-model.max-tokens:2048}")
    private Integer maxTokens;

    @Value("${langchain4j.dashscope.chat-model.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.dashscope.embedding-model.model-name:text-embedding-v3}")
    private String embeddingModelName;

    // ---- Milvus 配置 ----
    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private Integer milvusPort;

    @Value("${milvus.collection-name:tourism_knowledge}")
    private String collectionName;

    @Value("${milvus.dimension:1024}")
    private Integer dimension;

    @Value("${milvus.fail-fast:true}")
    private boolean milvusFailFast;

    // ---- RAG 配置 ----
    @Value("${rag.retrieval.top-k:8}")
    private Integer topK;

    @Value("${rag.retrieval.min-score:0.5}")
    private Double minScore;

    // ============================================================
    // 1. Embedding 模型 — 阿里 text-embedding-v3（1024维，中文优化）
    // ============================================================
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化 Embedding 模型: {} (维度: {})", embeddingModelName, dimension);
        return QwenEmbeddingModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(embeddingModelName)
                .build();
    }

    // ============================================================
    // 2. Milvus 向量数据库
    //
    // ⚠️ metadata 过滤关键点：
    //    - Milvus 自动将 TextSegment.metadata() 中的字段建为 JSON 字段
    //    - 过滤语法：Filter.equalTo("city", "qingdao")
    //    - 支持：equalTo / notEqualTo / isIn / between / and / or / not
    //    - 建议在 metadata 中至少包含：city / category / source / season
    // ============================================================
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("初始化 Milvus EmbeddingStore: {}:{}, collection: {}", milvusHost, milvusPort, collectionName);
        try {
            return MilvusEmbeddingStore.builder()
                    .host(milvusHost)
                    .port(milvusPort)
                    .collectionName(collectionName)
                    .dimension(dimension)
                    // COSINE 余弦相似度：中文语义检索推荐（INNER_PRODUCT 次选）
                    .metricType(MetricType.COSINE)
                    // FLAT 在开发环境更稳，避免索引构建时机导致启动失败
                    .indexType(IndexType.FLAT)
                    // 自动创建 collection（首次启动时）
                    .autoFlushOnInsert(true)
                    .build();
        } catch (Exception ex) {
            String msg = "Milvus 初始化失败 (" + milvusHost + ":" + milvusPort + "): " + ex.getMessage();
            if (milvusFailFast) {
                log.error("{}。milvus.fail-fast=true，应用拒绝启动。", msg, ex);
                throw new IllegalStateException(msg, ex);
            }
            log.warn("{}。milvus.fail-fast=false，回退 InMemory（不持久化，/actuator/health 将显示 Milvus DOWN）", msg);
            return new InMemoryEmbeddingStore<>();
        }
    }

    // ============================================================
    // 3. 对话模型 — Qwen-Max（高质量，适合生产）
    //    切换到 qwen-turbo 可降低延迟和费用
    // ============================================================
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("初始化 Chat 模型: {}", chatModelName);
        return QwenChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(chatModelName)
                .maxTokens(maxTokens)
                .temperature(temperature.floatValue())
                .topP(0.9)
                .build();
    }

    // ============================================================
    // 4. 流式对话模型 — 支持 SSE 实时输出
    // ============================================================
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return QwenStreamingChatModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(chatModelName)
                .maxTokens(maxTokens)
                .temperature(temperature.floatValue())
                .topP(0.9)
                .build();
    }

    // ============================================================
    // 5. 默认内容检索器（无城市过滤，跨城市查询使用）
    //    带城市过滤的版本在 RetrievalService.buildCityRetriever() 动态创建
    // ============================================================
    @Bean
    public EmbeddingStoreContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(minScore)
                .build();
    }

    // ============================================================
    // 6. 对话记忆工厂方法（每个 sessionId 独立实例）
    //    在 TourismChatService 中按 sessionId 管理
    // ============================================================
    public static MessageWindowChatMemory createMemory(String sessionId, int maxMessages) {
        return MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(maxMessages)
                .build();
    }

    // ============================================================
    // 7. CORS 配置（开发环境允许所有来源）
    //    生产环境应改为精确的前端域名
    // ============================================================
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
