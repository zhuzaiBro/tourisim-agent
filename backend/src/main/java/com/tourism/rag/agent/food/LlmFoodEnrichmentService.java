package com.tourism.rag.agent.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.FoodRecommendation;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当高德/小红书美食不足时，用 LLM 补充当地特色餐饮（非 Mock 静态表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmFoodEnrichmentService {

    private static final int MIN_TARGET = 3;

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public List<FoodRecommendation> enrichIfNeeded(String cityName, List<FoodRecommendation> existing,
                                                    double centerLat, double centerLng, int maxResults) {
        return enrichIfNeeded(cityName, existing, centerLat, centerLng, maxResults, null, null);
    }

    public List<FoodRecommendation> enrichIfNeeded(String cityName, List<FoodRecommendation> existing,
                                                    double centerLat, double centerLng, int maxResults,
                                                    List<String> dietaryRestrictions,
                                                    List<String> tastePreferences) {
        int size = existing != null ? existing.size() : 0;
        if (size >= Math.min(MIN_TARGET, maxResults)) {
            return existing != null ? existing : List.of();
        }
        int need = Math.max(MIN_TARGET, maxResults) - size;
        if (need <= 0) return existing != null ? existing : List.of();

        try {
            List<FoodRecommendation> generated = generateFoods(
                    cityName, need, centerLat, centerLng, dietaryRestrictions, tastePreferences);
            List<FoodRecommendation> merged = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            if (existing != null) {
                for (FoodRecommendation f : existing) {
                    String key = normalize(f.getName());
                    if (seen.add(key)) merged.add(f);
                }
            }
            for (FoodRecommendation f : generated) {
                String key = normalize(f.getName());
                if (seen.add(key)) merged.add(f);
            }
            log.info("[LlmFood] {} 补充 {} 家餐厅，合计 {}", cityName, generated.size(), merged.size());
            return merged.stream().limit(maxResults).toList();
        } catch (Exception e) {
            log.warn("[LlmFood] LLM 美食补充失败 {}: {}", cityName, e.getMessage());
            return existing != null ? existing : List.of();
        }
    }

    private List<FoodRecommendation> generateFoods(String cityName, int count,
                                                    double lat, double lng,
                                                    List<String> dietaryRestrictions,
                                                    List<String> tastePreferences) throws Exception {
        String constraints = FoodPreferenceHelper.toPromptConstraint(dietaryRestrictions, tastePreferences);
        String prompt = """
                你是美食向导。为目的地「%s」推荐 %d 家真实存在的特色餐厅或美食地标。
                只输出 JSON 数组：
                [{"name":"店名","category":"菜系","priceRange":"人均","mealType":"lunch","reason":"推荐理由"}]
                
                用户饮食要求：
                %s
                """.formatted(cityName, count, constraints);

        String raw = chatLanguageModel.generate(prompt).trim();
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("LLM 未返回 JSON 数组");
        }

        JsonNode arr = objectMapper.readTree(raw.substring(start, end + 1));
        List<FoodRecommendation> result = new ArrayList<>();
        int i = 0;
        for (JsonNode node : arr) {
            if (i >= count) break;
            result.add(FoodRecommendation.builder()
                    .name(node.path("name").asText("餐厅" + i))
                    .category(node.path("category").asText("当地美食"))
                    .rating(4.5)
                    .priceRange(node.path("priceRange").asText("价格待询"))
                    .distanceKm(1.0 + i * 0.4)
                    .address(cityName)
                    .businessStatus("营业中")
                    .openingHours("请提前确认")
                    .mealType(node.path("mealType").asText("lunch"))
                    .lat(lat + i * 0.003)
                    .lng(lng + i * 0.003)
                    .recommendReason(node.path("reason").asText("当地特色"))
                    .dataSource("llm_knowledge")
                    .build());
            i++;
        }
        return result;
    }

    private static String normalize(String name) {
        return name != null ? name.trim().toLowerCase() : "";
    }
}
