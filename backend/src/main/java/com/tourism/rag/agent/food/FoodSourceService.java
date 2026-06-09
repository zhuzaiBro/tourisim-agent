package com.tourism.rag.agent.food;

import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.provider.GaodeFoodProvider;
import com.tourism.rag.agent.provider.xhs.XiaohongshuFoodProvider;
import com.tourism.rag.dto.agent.FoodRecommendation;
import com.tourism.rag.dto.agent.FoodSearchResult;
import com.tourism.rag.util.CityGeoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一美食获取策略：高德 → 小红书 → LLM 补充。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodSourceService {

    private final GaodeFoodProvider gaodeFood;
    private final XiaohongshuFoodProvider xiaohongshuFood;
    private final LlmFoodEnrichmentService llmFoodEnrichment;
    private final CityGeoResolver cityGeoResolver;

    @Value("${agent.food.fallback-order:gaode,xhs,llm}")
    private String fallbackOrder;

    @Value("${agent.xhs.enabled:false}")
    private boolean xhsEnabled;

    public FoodSearchResult recommend(String cityCode, String cityName,
                                      double lat, double lng,
                                      List<String> preferences,
                                      List<String> dietaryRestrictions,
                                      List<String> tastePreferences,
                                      double minRating, int maxResults) {
        List<String> order = parseOrder(fallbackOrder);
        List<String> providersUsed = new ArrayList<>();
        List<FoodRecommendation> gaodeFoods = List.of();
        List<FoodRecommendation> xhsFoods = List.of();
        boolean llmEnriched = false;

        for (String step : order) {
            switch (step) {
                case "gaode" -> {
                    if (cityGeoResolver.isOverseas(cityCode, cityName)) {
                        log.info("[FoodSource] 境外目的地 {}，跳过高德美食搜索", cityName);
                    } else {
                        gaodeFoods = safeGaode(cityCode, cityName, lat, lng, preferences, minRating, maxResults);
                        if (!gaodeFoods.isEmpty()) {
                            providersUsed.add("gaode");
                        }
                    }
                }
                case "xhs" -> {
                    if (!xhsEnabled) {
                        log.info("[FoodSource][XHS] 已禁用，设置 XHS_ENABLED=true 开启");
                    } else if (!xiaohongshuFood.isConfigured()) {
                        log.warn("[FoodSource][XHS] 未配置有效 Cookie，跳过");
                    } else {
                        log.info("[FoodSource][XHS] 开始搜索美食 city={} 忌口={} 口味={}",
                                cityName, dietaryRestrictions, tastePreferences);
                        xhsFoods = xiaohongshuFood.searchFood(
                                cityCode, cityName, lat, lng, maxResults,
                                dietaryRestrictions, tastePreferences);
                        if (!xhsFoods.isEmpty()) {
                            providersUsed.add("xhs");
                            log.info("[FoodSource][XHS] 抽取到 {} 家餐厅", xhsFoods.size());
                        }
                    }
                }
                case "llm" -> { /* 合并后统一补充 */ }
                default -> log.warn("[FoodSource] 未知 fallback 步骤: {}", step);
            }
        }

        List<FoodRecommendation> merged = mergeSources(gaodeFoods, xhsFoods);
        int beforeLlm = merged.size();
        merged = llmFoodEnrichment.enrichIfNeeded(
                cityName, merged, lat, lng, maxResults, dietaryRestrictions, tastePreferences);
        merged = FoodPreferenceHelper.applyDietaryFilter(merged, dietaryRestrictions);
        if (merged.size() > beforeLlm) {
            llmEnriched = true;
            if (!providersUsed.contains("llm")) {
                providersUsed.add("llm");
            }
        }

        if (merged.isEmpty()) {
            throw new AgentDataUnavailableException(
                    "高德、小红书与 LLM 均未返回美食数据，请检查 MAP_API_KEY / XHS 配置: " + cityName);
        }

        String primary = resolvePrimaryProvider(gaodeFoods, xhsFoods, llmEnriched);
        boolean usedFallback = gaodeFoods.isEmpty();

        return FoodSearchResult.builder()
                .foods(merged.stream().limit(maxResults).collect(Collectors.toList()))
                .primaryProvider(primary)
                .usedFallback(usedFallback)
                .providersUsed(providersUsed)
                .build();
    }

    private List<FoodRecommendation> safeGaode(String cityCode, String cityName,
                                                double lat, double lng,
                                                List<String> preferences,
                                                double minRating, int maxResults) {
        try {
            return gaodeFood.recommendFood(
                    cityCode, cityName, lat, lng, "lunch", preferences, minRating, maxResults);
        } catch (Exception e) {
            log.warn("[FoodSource][Gaode] 美食搜索失败 {}: {}", cityName, e.getMessage());
            return List.of();
        }
    }

    private List<FoodRecommendation> mergeSources(List<FoodRecommendation> gaodeFoods,
                                                   List<FoodRecommendation> xhsFoods) {
        // 小红书攻略优先展示（同名时保留 XHS 推荐理由与口碑）
        Map<String, FoodRecommendation> byName = new LinkedHashMap<>();
        for (FoodRecommendation x : xhsFoods) {
            byName.put(normalize(x.getName()), x);
        }
        for (FoodRecommendation g : gaodeFoods) {
            String key = normalize(g.getName());
            if (!byName.containsKey(key)) {
                byName.put(key, g);
            } else {
                FoodRecommendation existing = byName.get(key);
                if (isXhsSource(existing.getDataSource()) && g.getLat() != 0 && g.getLng() != 0) {
                    existing.setLat(g.getLat());
                    existing.setLng(g.getLng());
                    if (existing.getAddress() == null || existing.getAddress().isBlank()) {
                        existing.setAddress(g.getAddress());
                    }
                    existing.setDataSource("xhs_guide+gaode_api");
                }
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static boolean isXhsSource(String ds) {
        return ds != null && ds.contains("xhs");
    }

    private String resolvePrimaryProvider(List<FoodRecommendation> gaodeFoods,
                                          List<FoodRecommendation> xhsFoods,
                                          boolean llmEnriched) {
        List<String> parts = new ArrayList<>();
        if (!gaodeFoods.isEmpty()) parts.add("gaode_api");
        if (!xhsFoods.isEmpty()) parts.add("xhs");
        if (llmEnriched) parts.add("llm");
        if (parts.isEmpty()) return "llm_knowledge";
        return String.join("+", parts);
    }

    private static String normalize(String name) {
        return name != null ? name.trim().toLowerCase() : "";
    }

    private List<String> parseOrder(String order) {
        return Arrays.stream(order.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
