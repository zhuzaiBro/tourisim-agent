package com.tourism.rag.service;

import com.tourism.rag.dto.IngestJobStatus;
import com.tourism.rag.entity.Attraction;
import com.tourism.rag.helper.DocumentLoaderHelper;
import com.tourism.rag.repository.AttractionRepository;
import com.tourism.rag.repository.CityRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 数据摄入 Service
 *
 * 处理流程：
 * 1. 加载文档（文件/文本/数据库）→ Document
 * 2. Recursive Splitting（chunkSize ~700，overlap 150）→ List<TextSegment>
 * 3. Embedding（Qwen text-embedding-v3）→ List<Embedding>
 * 4. 写入 Milvus（携带 metadata）
 * 5. 更新 MySQL city 表状态
 *
 * 【多城市扩展方法】
 * 新增城市（以北京为例）：
 * 1. 在 MySQL city 表插入：INSERT INTO city (code, name_cn, ...) VALUES ('beijing', '北京', ...)
 * 2. 准备知识库文档（markdown / pdf）
 * 3. 调用 POST /api/ingest/city {cityCode: "beijing", sourceType: "FILE", filePath: "..."}
 * 无需修改任何代码！
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentLoaderHelper documentLoaderHelper;
    private final CityRepository cityRepository;
    private final AttractionRepository attractionRepository;

    @Value("${rag.retrieval.chunk-size:700}")
    private int chunkSize;

    @Value("${rag.retrieval.chunk-overlap:150}")
    private int chunkOverlap;

    private final ConcurrentHashMap<String, IngestJobStatus> ingestJobs = new ConcurrentHashMap<>();

    /**
     * 初始化青岛知识库（含预设数据）
     * 接口：POST /api/ingest/qingdao
     */
    @Async
    @Transactional
    public CompletableFuture<Integer> ingestQingdaoData() {
        log.info("开始摄入青岛旅游知识库...");

        List<Document> documents = buildQingdaoDocuments();

        // 同时写入 MySQL 景点数据
        initQingdaoAttractions();

        int count = ingestDocuments(documents);

        // 更新城市状态
        cityRepository.findByCode("qingdao").ifPresent(city -> {
            city.setKnowledgeIngested(true);
            city.setEnabled(true);
            cityRepository.save(city);
        });

        log.info("青岛知识库摄入完成，共处理 {} 个文档块", count);
        return CompletableFuture.completedFuture(count);
    }

    /**
     * 从本地文件摄入指定城市数据
     */
    @Async
    public CompletableFuture<Integer> ingestFromFile(String cityCode, String category, Path filePath) {
        log.info("从文件摄入 - 城市: {}, 分类: {}, 文件: {}", cityCode, category, filePath);
        Document doc = documentLoaderHelper.loadFromFile(filePath, cityCode, category);
        int count = ingestDocuments(List.of(doc));
        updateCityStatus(cityCode);
        return CompletableFuture.completedFuture(count);
    }

    /**
     * 从 MySQL attraction 表向量化摄入
     * 用于将结构化数据转为语义可检索的向量
     */
    @Async
    public CompletableFuture<Integer> ingestFromDatabase(String cityCode) {
        List<Attraction> attractions = attractionRepository.findByCityCode(cityCode);
        log.info("从数据库摄入 - 城市: {}, 景点数量: {}", cityCode, attractions.size());

        List<Document> documents = attractions.stream().map(a -> {
            Map<String, Object> record = new HashMap<>();
            record.put("id", a.getId());
            record.put("name", a.getName());
            record.put("category", a.getCategory());
            record.put("description", a.getDescription());
            record.put("address", a.getAddress());
            record.put("ticketPrice", a.getTicketPrice());
            record.put("openingHours", a.getOpeningHours());
            record.put("seasons", a.getSeasons());
            return documentLoaderHelper.createFromDatabaseRecord(record, cityCode);
        }).toList();

        int count = ingestDocuments(documents);
        updateCityStatus(cityCode);
        return CompletableFuture.completedFuture(count);
    }

    // ============================================================
    // 核心摄入逻辑
    // ============================================================

    /**
     * 通用文档摄入方法
     * 分块 → Embedding → 写入 Milvus
     *
     * @return 写入的文档块数量
     */
    private int ingestDocuments(List<Document> documents) {
        // Recursive Character Text Splitter
        // 优先在段落(\n\n)、句子(\n)、标点处断开，保持语义完整性
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);

        List<TextSegment> allSegments = new ArrayList<>();
        for (Document doc : documents) {
            List<TextSegment> segments = splitter.split(doc);
            log.debug("文档分块完成: {} 个 chunks，metadata: {}",
                    segments.size(), doc.metadata().toMap());
            allSegments.addAll(segments);
        }

        if (allSegments.isEmpty()) {
            log.warn("没有可摄入的文档块");
            return 0;
        }

        // 批量 Embedding（避免单次请求过多 tokens）
        // 每批 32 个 chunk（根据 DashScope API 限制调整）
        int batchSize = 32;
        int totalIngested = 0;

        for (int i = 0; i < allSegments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allSegments.size());
            List<TextSegment> batch = allSegments.subList(i, end);

            // 调用 Qwen Embedding API
            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();

            // 写入 Milvus（metadata 会自动携带 city/category/source 等字段）
            embeddingStore.addAll(embeddings, batch);

            totalIngested += batch.size();
            log.debug("已写入 {}/{} 个文档块到 Milvus", totalIngested, allSegments.size());
        }

        return totalIngested;
    }

    private void updateCityStatus(String cityCode) {
        cityRepository.findByCode(cityCode).ifPresent(city -> {
            city.setKnowledgeIngested(true);
            city.setEnabled(true);
            cityRepository.save(city);
        });
    }

    // ============================================================
    // 向量库管理
    // ============================================================

    public String startJob(String cityCode, String message) {
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ingestJobs.put(jobId, IngestJobStatus.builder()
                .jobId(jobId)
                .cityCode(cityCode)
                .status("processing")
                .message(message)
                .build());
        return jobId;
    }

    public Optional<IngestJobStatus> getJob(String jobId) {
        return Optional.ofNullable(ingestJobs.get(jobId));
    }

    private void finishJob(String jobId, int chunks) {
        ingestJobs.computeIfPresent(jobId, (id, job) -> IngestJobStatus.builder()
                .jobId(job.getJobId())
                .cityCode(job.getCityCode())
                .status("completed")
                .message("摄入完成")
                .chunks(chunks)
                .finishedAtMs(System.currentTimeMillis())
                .build());
    }

    private void failJob(String jobId, String message) {
        ingestJobs.computeIfPresent(jobId, (id, job) -> IngestJobStatus.builder()
                .jobId(job.getJobId())
                .cityCode(job.getCityCode())
                .status("failed")
                .message(message)
                .finishedAtMs(System.currentTimeMillis())
                .build());
    }

    /**
     * 按城市 metadata 删除 Milvus 向量。
     */
    public void deleteVectorsByCity(String cityCode) {
        String normalized = cityCode.toLowerCase().trim();
        Filter filter = metadataKey("city").isEqualTo(normalized);
        embeddingStore.removeAll(filter);
        log.info("已删除城市 [{}] 的向量数据", normalized);

        cityRepository.findByCode(normalized).ifPresent(city -> {
            city.setKnowledgeIngested(false);
            cityRepository.save(city);
        });
    }

    @Async
    public CompletableFuture<Integer> reindexCity(String cityCode, String jobId) {
        try {
            deleteVectorsByCity(cityCode);
            int count;
            if ("qingdao".equalsIgnoreCase(cityCode)) {
                initQingdaoAttractions();
                count = ingestDocuments(buildQingdaoDocuments());
            } else {
                count = ingestFromDatabaseSync(cityCode);
            }
            updateCityStatus(cityCode);
            finishJob(jobId, count);
            log.info("城市 [{}] 重建索引完成，{} 个文档块", cityCode, count);
            return CompletableFuture.completedFuture(count);
        } catch (Exception e) {
            failJob(jobId, e.getMessage());
            log.error("城市 [{}] 重建索引失败", cityCode, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private int ingestFromDatabaseSync(String cityCode) {
        List<Attraction> attractions = attractionRepository.findByCityCode(cityCode);
        List<Document> documents = attractions.stream().map(a -> {
            Map<String, Object> record = new HashMap<>();
            record.put("id", a.getId());
            record.put("name", a.getName());
            record.put("category", a.getCategory());
            record.put("description", a.getDescription());
            record.put("address", a.getAddress());
            record.put("ticketPrice", a.getTicketPrice());
            record.put("openingHours", a.getOpeningHours());
            record.put("seasons", a.getSeasons());
            return documentLoaderHelper.createFromDatabaseRecord(record, cityCode);
        }).toList();
        return ingestDocuments(documents);
    }

    // ============================================================
    // 青岛知识库（classpath markdown）
    // ============================================================

    private static final String QINGDAO_KNOWLEDGE_RESOURCE = "data/qingdao_knowledge.md";

    private List<Document> buildQingdaoDocuments() {
        log.info("从 classpath 加载青岛知识库: {}", QINGDAO_KNOWLEDGE_RESOURCE);
        return documentLoaderHelper.loadMarkdownSectionsFromClasspath(QINGDAO_KNOWLEDGE_RESOURCE, "qingdao");
    }

    /**
     * 初始化青岛景点到 MySQL attraction 表
     */
    @Transactional
    protected void initQingdaoAttractions() {
        if (attractionRepository.findByCityCode("qingdao").isEmpty()) {
            List<Attraction> attractions = List.of(
                    Attraction.builder()
                            .cityCode("qingdao").name("栈桥").category("attraction")
                            .description("青岛标志性景点，440米海上长桥，南端建有回澜阁")
                            .address("青岛市市南区太平路22号")
                            .ticketPrice(BigDecimal.ZERO)
                            .openingHours("全天开放（回澜阁 8:30-17:00）")
                            .seasons("all").rating(new BigDecimal("4.7")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("八大关景区").category("attraction")
                            .description("万国建筑博览会，浪漫街区，四季美景各异")
                            .address("青岛市市南区山海关路1号")
                            .ticketPrice(BigDecimal.ZERO)
                            .openingHours("全天开放")
                            .seasons("spring,autumn").rating(new BigDecimal("4.8")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("崂山风景区").category("attraction")
                            .description("海上名山第一，道教圣地，主峰海拔1132米")
                            .address("青岛市崂山区")
                            .ticketPrice(new BigDecimal("165"))
                            .openingHours("7:00-17:00")
                            .seasons("all").rating(new BigDecimal("4.9")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("青岛啤酒博物馆").category("attraction")
                            .description("百年啤酒文化，原浆品鉴，工业遗址改造")
                            .address("青岛市市北区登州路56号")
                            .ticketPrice(new BigDecimal("60"))
                            .openingHours("8:30-17:30")
                            .seasons("all").rating(new BigDecimal("4.5")).recommended(true).build(),

                    Attraction.builder()
                            .cityCode("qingdao").name("劈柴院").category("food")
                            .description("百年历史小吃街，海鲜锅贴、蛤蜊、散啤聚集地")
                            .address("青岛市市南区江宁路")
                            .ticketPrice(BigDecimal.ZERO)
                            .openingHours("9:00-22:00")
                            .seasons("all").rating(new BigDecimal("4.3")).recommended(true).build()
            );
            attractionRepository.saveAll(attractions);
            log.info("青岛景点数据初始化完成，共 {} 条", attractions.size());
        }
    }
}
