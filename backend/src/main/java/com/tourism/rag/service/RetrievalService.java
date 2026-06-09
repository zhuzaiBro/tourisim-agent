package com.tourism.rag.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 检索 Service
 *
 * 【metadata 过滤核心逻辑】
 *
 * Milvus 过滤器语法（LangChain4j Filter API）：
 *
 *   单城市过滤：
 *     metadataKey("city").isEqualTo("qingdao")
 *
 *   多城市过滤（联游场景）：
 *     metadataKey("city").isIn("qingdao", "beijing")
 *
 *   城市+分类过滤：
 *     metadataKey("city").isEqualTo("qingdao")
 *       .and(metadataKey("category").isEqualTo("food"))
 *
 *   季节过滤：
 *     metadataKey("season").isIn("summer", "all")
 *
 * ⚠️ 注意：Milvus 中 metadata 字段必须在摄入时存在，否则过滤无效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.retrieval.top-k:8}")
    private Integer topK;

    @Value("${rag.retrieval.min-score:0.5}")
    private Double minScore;

    /**
     * 带城市过滤的检索
     * 这是最常用的检索方法，隔离不同城市的知识
     *
     * @param query  用户查询文本
     * @param cities 城市编码列表（如 ["qingdao"] 或 ["qingdao", "beijing"]）
     * @return 检索到的内容列表（含 metadata）
     */
    public List<Content> retrieveWithCityFilter(String query, List<String> cities) {
        Filter filter = buildCityFilter(cities);
        return buildRetriever(filter).retrieve(Query.from(query));
    }

    /**
     * 带城市+分类过滤的检索
     * 用于精确查询（如"只看美食推荐"、"只看交通信息"）
     */
    public List<Content> retrieveWithCategoryFilter(String query, List<String> cities, String category) {
        Filter cityFilter = buildCityFilter(cities);
        Filter categoryFilter = metadataKey("category").isEqualTo(category);

        // AND 组合过滤器
        Filter combinedFilter = cityFilter != null
                ? cityFilter.and(categoryFilter)
                : categoryFilter;

        return buildRetriever(combinedFilter).retrieve(Query.from(query));
    }

    /**
     * 无过滤检索（全库检索，跨城市比较场景）
     */
    public List<Content> retrieveGlobal(String query) {
        return buildRetriever(null).retrieve(Query.from(query));
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 构建城市过滤器
     *
     * 单城市：equalTo
     * 多城市：isIn（支持联游场景，如"青岛+北京7天联游"）
     * 空城市列表：返回 null（无过滤，全库检索）
     */
    private Filter buildCityFilter(List<String> cities) {
        if (CollectionUtils.isEmpty(cities)) {
            return null;
        }

        if (cities.size() == 1) {
            // 单城市精确匹配
            String city = cities.get(0).toLowerCase().trim();
            log.debug("应用单城市过滤器: city={}", city);
            return metadataKey("city").isEqualTo(city);
        } else {
            // 多城市 IN 查询
            String[] cityArray = cities.stream()
                    .map(String::toLowerCase)
                    .map(String::trim)
                    .toArray(String[]::new);
            log.debug("应用多城市过滤器: cities={}", cities);
            return metadataKey("city").isIn(cityArray);
        }
    }

    /**
     * 动态构建带过滤器的 ContentRetriever
     * 每次查询构建新实例（过滤器是查询级别的，不能复用）
     */
    private EmbeddingStoreContentRetriever buildRetriever(Filter filter) {
        var builder = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(minScore);

        if (filter != null) {
            builder.filter(filter);
        }

        return builder.build();
    }
}
