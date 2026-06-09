package com.tourism.rag.agent.accommodation;

import com.tourism.rag.agent.provider.GaodeAccommodationProvider;
import com.tourism.rag.dto.agent.AccommodationRecommendation;
import com.tourism.rag.dto.agent.AccommodationSearchResult;
import com.tourism.rag.util.CityGeoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 住宿推荐：高德 POI → LLM 补充（境外跳过高德）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationSourceService {

    private final GaodeAccommodationProvider gaodeAccommodation;
    private final LlmAccommodationEnrichmentService llmEnrichment;
    private final CityGeoResolver cityGeoResolver;

    @Value("${agent.accommodation.fallback-order:gaode,llm}")
    private String fallbackOrder;

    @Value("${agent.accommodation.min-rating:3.8}")
    private double minRating;

    @Value("${agent.accommodation.max-results:5}")
    private int maxResults;

    public AccommodationSearchResult recommend(String cityCode, String cityName,
                                                  double lat, double lng,
                                                  String accommodationType,
                                                  String budget,
                                                  List<String> preferences) {
        List<String> order = parseOrder(fallbackOrder);
        List<String> providersUsed = new ArrayList<>();
        List<AccommodationRecommendation> gaodeList = List.of();
        boolean usedFallback = false;

        for (String step : order) {
            switch (step) {
                case "gaode" -> {
                    if (cityGeoResolver.isOverseas(cityCode, cityName)) {
                        log.info("[AccommodationSource] 境外 {}，跳过高德住宿搜索", cityName);
                    } else {
                        gaodeList = safeGaode(cityName, lat, lng, accommodationType, budget);
                        if (!gaodeList.isEmpty()) providersUsed.add("gaode");
                    }
                }
                case "llm" -> { /* 合并后统一补充 */ }
                default -> log.warn("[AccommodationSource] 未知步骤: {}", step);
            }
        }

        List<AccommodationRecommendation> merged = new ArrayList<>(gaodeList);
        if (order.contains("llm")) {
            int before = merged.size();
            merged = llmEnrichment.enrichIfNeeded(
                    cityName, merged, accommodationType, budget, lat, lng, maxResults);
            if (merged.size() > before) {
                providersUsed.add("llm");
                usedFallback = gaodeList.isEmpty();
            }
        }

        if (merged.isEmpty()) {
            return AccommodationSearchResult.builder()
                    .accommodations(List.of())
                    .tips(List.of("暂未检索到住宿，建议自行在 OTA 平台预订市中心酒店"))
                    .primaryProvider("none")
                    .usedFallback(true)
                    .providersUsed(providersUsed)
                    .build();
        }

        // 标记首推：高评分 + 近距离
        merged.get(0).setPrimaryPick(true);
        AccommodationRecommendation primary = merged.get(0);

        String primaryProvider = providersUsed.isEmpty() ? "llm_knowledge"
                : String.join("+", providersUsed);

        return AccommodationSearchResult.builder()
                .accommodations(merged)
                .primary(primary)
                .tips(buildTips(cityName, primary, budget, preferences))
                .primaryProvider(primaryProvider)
                .usedFallback(usedFallback)
                .providersUsed(providersUsed)
                .build();
    }

    private List<AccommodationRecommendation> safeGaode(String cityName, double lat, double lng,
                                                         String accommodationType, String budget) {
        try {
            return gaodeAccommodation.search(
                    cityName, lat, lng, accommodationType, budget, minRating, maxResults);
        } catch (Exception e) {
            log.warn("[AccommodationSource][Gaode] 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> buildTips(String cityName, AccommodationRecommendation primary,
                                    String budget, List<String> preferences) {
        List<String> tips = new ArrayList<>();
        tips.add("首推入住「" + primary.getName() + "」"
                + (primary.getDistrict() != null && !primary.getDistrict().isBlank()
                ? "（" + primary.getDistrict() + "）" : "")
                + "，便于串联每日行程");
        tips.add("建议提前 3–7 天预订，节假日与旺季房源紧张");
        if (preferences != null && preferences.contains("family")) {
            tips.add("亲子出行优先选含早餐、近地铁/停车方便的酒店");
        }
        if ("low".equals(budget)) {
            tips.add("经济档可关注连锁快捷酒店与地铁沿线民宿");
        } else if ("high".equals(budget)) {
            tips.add("高端档可优先考虑景观房与含行政酒廊的星级酒店");
        }
        tips.add(cityName + "行程期间可将酒店作为每日出发点，减少搬运行李");
        return tips;
    }

    private static List<String> parseOrder(String order) {
        if (order == null || order.isBlank()) return List.of("gaode", "llm");
        return List.of(order.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
