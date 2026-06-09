package com.tourism.rag.agent.accommodation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.AccommodationRecommendation;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 高德住宿不足或境外目的地时，用 LLM 补充住宿建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAccommodationEnrichmentService {

    private static final int MIN_TARGET = 3;

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public List<AccommodationRecommendation> enrichIfNeeded(String cityName,
                                                            List<AccommodationRecommendation> existing,
                                                            String accommodationType,
                                                            String budget,
                                                            double centerLat,
                                                            double centerLng,
                                                            int maxResults) {
        int size = existing != null ? existing.size() : 0;
        if (size >= Math.min(MIN_TARGET, maxResults)) {
            return existing != null ? existing : List.of();
        }
        int need = Math.max(MIN_TARGET, maxResults) - size;
        if (need <= 0) return existing != null ? existing : List.of();

        try {
            List<AccommodationRecommendation> generated = generate(
                    cityName, need, accommodationType, budget, centerLat, centerLng);
            List<AccommodationRecommendation> merged = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            if (existing != null) {
                for (AccommodationRecommendation a : existing) {
                    if (seen.add(normalize(a.getName()))) merged.add(a);
                }
            }
            for (AccommodationRecommendation a : generated) {
                if (seen.add(normalize(a.getName()))) merged.add(a);
            }
            log.info("[LlmAccommodation] {} 补充 {} 家住宿，合计 {}", cityName, generated.size(), merged.size());
            return merged.stream().limit(maxResults).toList();
        } catch (Exception e) {
            log.warn("[LlmAccommodation] 补充失败: {}", e.getMessage());
            return existing != null ? existing : List.of();
        }
    }

    private List<AccommodationRecommendation> generate(String cityName, int count,
                                                        String accommodationType, String budget,
                                                        double lat, double lng) throws Exception {
        String typeHint = switch (accommodationType != null ? accommodationType : "hotel") {
            case "hostel" -> "青年旅舍或背包客客栈";
            case "homestay" -> "特色民宿或家庭旅馆";
            default -> "酒店";
        };
        String budgetHint = switch (budget != null ? budget : "medium") {
            case "low" -> "经济型，每晚150-300元";
            case "high" -> "高端精品或星级酒店，每晚600元以上";
            default -> "舒适型，每晚300-600元";
        };

        String prompt = """
                你是旅行住宿顾问。为前往【%s】的旅客推荐 %d 家真实存在的%s。
                预算档次：%s。优先市中心或主要景点附近、交通便利的区域。
                仅输出 JSON 数组，每项字段：name, category, starLevel, priceRange, district, address, recommendReason。
                不要 markdown，不要解释。""".formatted(cityName, count, typeHint, budgetHint);

        String raw = chatLanguageModel.generate(prompt);
        String json = extractJsonArray(raw);
        JsonNode arr = objectMapper.readTree(json);
        List<AccommodationRecommendation> list = new ArrayList<>();
        for (JsonNode node : arr) {
            list.add(AccommodationRecommendation.builder()
                    .name(node.path("name").asText())
                    .category(node.path("category").asText("酒店"))
                    .starLevel(node.path("starLevel").asText(""))
                    .rating(4.2)
                    .priceRange(node.path("priceRange").asText(budgetHint))
                    .distanceKm(1.5)
                    .district(node.path("district").asText(""))
                    .address(node.path("address").asText(""))
                    .checkInTip("AI 推荐，请自行核实房态与价格")
                    .recommendReason(node.path("recommendReason").asText("当地口碑住宿"))
                    .lat(lat).lng(lng)
                    .dataSource("llm_knowledge")
                    .primaryPick(false)
                    .build());
        }
        return list;
    }

    private static String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start >= 0 && end > start) return raw.substring(start, end + 1);
        return "[]";
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
