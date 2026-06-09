package com.tourism.rag.agent.poi;

import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.provider.GaodeMapProvider;
import com.tourism.rag.agent.provider.xhs.XiaohongshuGuideProvider;
import com.tourism.rag.agent.rag.RagPoiParser;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.util.CityGeoResolver;
import com.tourism.rag.dto.agent.PoiSearchResult;
import com.tourism.rag.service.RetrievalService;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一 POI 获取策略：高德 → RAG 合并（禁用 Mock 兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoiSourceService {

    private final GaodeMapProvider gaodeMap;
    private final RetrievalService retrievalService;
    private final RagPoiParser ragPoiParser;
    private final LlmPoiEnrichmentService llmPoiEnrichment;
    private final CityGeoResolver cityGeoResolver;
    private final XiaohongshuGuideProvider xiaohongshuGuide;

    @Value("${agent.poi.fallback-order:gaode,rag,xhs}")
    private String fallbackOrder;

    @Value("${agent.poi.rag-enabled:true}")
    private boolean ragEnabled;

    @Value("${agent.xhs.enabled:false}")
    private boolean xhsEnabled;

    public PoiSearchResult search(String cityCode, String cityName,
                                  List<String> keywords, List<String> preferences,
                                  int maxResults, int totalDays) {
        List<String> order = parseOrder(fallbackOrder);
        List<String> providersUsed = new ArrayList<>();
        List<PoiInfo> gaodePois = List.of();
        List<PoiInfo> ragPois = List.of();
        List<PoiInfo> xhsPois = List.of();

        for (String step : order) {
            if ("mock".equals(step)) {
                log.warn("[PoiSource] 已禁用 Mock，忽略 fallback 步骤 mock");
                continue;
            }
            switch (step) {
                case "gaode" -> {
                    if (cityGeoResolver.isOverseas(cityCode, cityName)) {
                        log.info("[PoiSource] 境外目的地 {}，跳过高德 POI 搜索", cityName);
                    } else {
                        gaodePois = gaodeMap.searchPOI(cityCode, cityName, keywords, preferences, maxResults);
                        if (!gaodePois.isEmpty()) {
                            providersUsed.add("gaode");
                        }
                    }
                }
                case "rag" -> {
                    if (ragEnabled) {
                        ragPois = searchFromRag(cityCode, cityName, keywords, maxResults);
                        if (!ragPois.isEmpty()) {
                            providersUsed.add("rag");
                        }
                    }
                }
                case "xhs" -> {
                    if (!xhsEnabled) {
                        log.info("[PoiSource][XHS] 已禁用，设置 XHS_ENABLED=true 开启");
                    } else if (!xiaohongshuGuide.isConfigured()) {
                        log.warn("[PoiSource][XHS] 未配置有效 Cookie，跳过（检查 .env 中 XHS_COOKIE 是否为空或被后续行覆盖）");
                    } else {
                        log.info("[PoiSource][XHS] 开始搜索攻略 city={}", cityName);
                        xhsPois = xiaohongshuGuide.searchPois(
                                cityCode, cityName, keywords, maxResults, Math.max(1, totalDays));
                        if (!xhsPois.isEmpty()) {
                            providersUsed.add("xhs");
                            log.info("[PoiSource][XHS] 抽取到 {} 个景点", xhsPois.size());
                        } else {
                            log.warn("[PoiSource][XHS] 未获取到景点，请检查 Cookie 是否用双引号包裹、xhshow 是否安装、签名是否成功");
                        }
                    }
                }
                default -> log.warn("[PoiSource] 未知 fallback 步骤: {}", step);
            }
        }

        double[] center = cityGeoResolver.resolve(cityCode, cityName);
        List<PoiInfo> merged = mergeAllSources(gaodePois, ragPois, xhsPois, center);
        merged = llmPoiEnrichment.enrichIfNeeded(cityCode, cityName, merged, center[0], center[1]);

        boolean ragEnriched = !ragPois.isEmpty() && merged.stream().anyMatch(p ->
                p.getDataSource() != null && p.getDataSource().contains("rag"));
        boolean xhsEnriched = !xhsPois.isEmpty() && merged.stream().anyMatch(p ->
                p.getDataSource() != null && p.getDataSource().contains("xhs"));
        boolean llmEnriched = merged.stream().anyMatch(p ->
                "llm_knowledge".equals(p.getDataSource()));

        if (merged.isEmpty()) {
            throw new AgentDataUnavailableException(
                    "高德、RAG、小红书与 LLM 均未返回景点数据，请检查 MAP_API_KEY / XHS 配置: " + cityName);
        }

        String primaryProvider = resolvePrimaryProvider(gaodePois, ragPois, xhsPois, ragEnriched, xhsEnriched, llmEnriched);

        return PoiSearchResult.builder()
                .pois(merged.stream().limit(maxResults).collect(Collectors.toList()))
                .primaryProvider(primaryProvider)
                .usedFallback(false)
                .ragEnriched(ragEnriched)
                .providersUsed(providersUsed)
                .build();
    }

    private List<PoiInfo> searchFromRag(String cityCode, String cityName,
                                        List<String> keywords, int maxResults) {
        try {
            String kw = keywords != null && !keywords.isEmpty()
                    ? String.join(" ", keywords) : "景点";
            String query = cityName + " " + kw + " 旅游 门票 开放时间";
            List<Content> contents = retrievalService.retrieveWithCategoryFilter(
                    query, List.of(cityCode), "attraction");
            List<PoiInfo> pois = ragPoiParser.parseFromContents(contents, cityCode);
            log.info("[PoiSource][RAG] city={}, 解析到 {} 个景点", cityCode, pois.size());
            return pois.stream().limit(maxResults).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[PoiSource][RAG] 检索失败 city={}: {}", cityCode, e.getMessage());
            return List.of();
        }
    }

    private List<PoiInfo> mergeAllSources(List<PoiInfo> gaodePois, List<PoiInfo> ragPois,
                                           List<PoiInfo> xhsPois, double[] center) {
        if (gaodePois.isEmpty() && ragPois.isEmpty() && xhsPois.isEmpty()) {
            return List.of();
        }

        Map<String, PoiInfo> ragByName = new LinkedHashMap<>();
        for (PoiInfo rag : ragPois) {
            ragByName.put(ragPoiParser.normalizeName(rag.getName()), rag);
        }

        List<PoiInfo> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 小红书攻略景点优先（攻略向 POI 排在前列，便于行程编排选用）
        for (PoiInfo xhs : xhsPois) {
            String key = ragPoiParser.normalizeName(xhs.getName());
            if (!seen.contains(key)) {
                PoiInfo copy = copyPoi(xhs);
                PoiInfo rag = ragByName.get(key);
                if (rag != null) {
                    ragPoiParser.enrich(copy, rag);
                    if (copy.getDataSource() != null && copy.getDataSource().contains("xhs")) {
                        copy.setDataSource(copy.getDataSource().contains("gaode")
                                ? copy.getDataSource() + "+rag" : "xhs_guide+rag");
                    } else {
                        copy.setDataSource("xhs_guide+rag");
                    }
                }
                fillCenterIfMissing(copy, center);
                merged.add(copy);
                seen.add(key);
            }
        }

        for (PoiInfo gaode : gaodePois) {
            String key = ragPoiParser.normalizeName(gaode.getName());
            if (seen.contains(key)) {
                PoiInfo existing = merged.stream()
                        .filter(p -> key.equals(ragPoiParser.normalizeName(p.getName())))
                        .findFirst().orElse(null);
                if (existing != null && isXhsSource(existing.getDataSource())) {
                    ragPoiParser.enrich(existing, gaode);
                    if (!existing.getDataSource().contains("gaode")) {
                        existing.setDataSource("xhs_guide+gaode_api");
                    }
                }
                continue;
            }
            PoiInfo copy = copyPoi(gaode);
            PoiInfo rag = ragByName.get(key);
            if (rag != null) {
                ragPoiParser.enrich(copy, rag);
                copy.setDataSource("gaode_api+rag");
            }
            merged.add(copy);
            seen.add(key);
        }

        for (PoiInfo rag : ragPois) {
            String key = ragPoiParser.normalizeName(rag.getName());
            if (!seen.contains(key)) {
                PoiInfo copy = copyPoi(rag);
                fillCenterIfMissing(copy, center);
                merged.add(copy);
                seen.add(key);
            }
        }

        return merged;
    }

    private static boolean isXhsSource(String ds) {
        return ds != null && ds.contains("xhs");
    }

    private void fillCenterIfMissing(PoiInfo poi, double[] center) {
        if (poi.getLat() == 0 && poi.getLng() == 0) {
            poi.setLat(center[0]);
            poi.setLng(center[1]);
        }
    }

    private String resolvePrimaryProvider(List<PoiInfo> gaodePois, List<PoiInfo> ragPois, List<PoiInfo> xhsPois,
                                          boolean ragEnriched, boolean xhsEnriched, boolean llmEnriched) {
        List<String> parts = new ArrayList<>();
        if (!gaodePois.isEmpty()) parts.add("gaode_api");
        if (ragEnriched || !ragPois.isEmpty()) parts.add("rag");
        if (xhsEnriched || !xhsPois.isEmpty()) parts.add("xhs");
        if (llmEnriched) parts.add("llm");
        if (parts.isEmpty()) return "llm_knowledge";
        return String.join("+", parts);
    }

    private PoiInfo copyPoi(PoiInfo src) {
        return PoiInfo.builder()
                .id(src.getId())
                .name(src.getName())
                .category(src.getCategory())
                .address(src.getAddress())
                .lat(src.getLat())
                .lng(src.getLng())
                .rating(src.getRating())
                .openingHours(src.getOpeningHours())
                .ticketPrice(src.getTicketPrice())
                .visitDurationMinutes(src.getVisitDurationMinutes())
                .indoorVenue(src.isIndoorVenue())
                .tags(src.getTags())
                .description(src.getDescription())
                .dataSource(src.getDataSource())
                .build();
    }

    private List<String> parseOrder(String order) {
        return Arrays.stream(order.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
