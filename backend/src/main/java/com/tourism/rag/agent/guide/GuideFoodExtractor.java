package com.tourism.rag.agent.guide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.food.FoodPreferenceHelper;
import com.tourism.rag.dto.agent.FoodRecommendation;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 从小红书攻略笔记中抽取结构化餐厅/美食推荐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuideFoodExtractor {

    private static final Set<String> VALID_MEALS = Set.of(
            "breakfast", "lunch", "dinner", "snack");

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public List<FoodRecommendation> extractFromNotes(String cityName, List<XiaohongshuNote> notes,
                                                      double centerLat, double centerLng,
                                                      int maxResults) {
        return extractFromNotes(cityName, notes, centerLat, centerLng, maxResults, null, null);
    }

    public List<FoodRecommendation> extractFromNotes(String cityName, List<XiaohongshuNote> notes,
                                                      double centerLat, double centerLng,
                                                      int maxResults,
                                                      List<String> dietaryRestrictions,
                                                      List<String> tastePreferences) {
        if (notes == null || notes.isEmpty()) return List.of();

        List<XiaohongshuNote> top = notes.stream().limit(12).toList();
        StringBuilder corpus = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            XiaohongshuNote n = top.get(i);
            corpus.append(i + 1).append(". 《").append(n.getTitle()).append("》");
            if (n.getDescription() != null && !n.getDescription().isBlank()) {
                corpus.append(" — ").append(truncate(n.getDescription(), 220));
            }
            corpus.append("\n");
        }

        try {
            return parseWithLlm(cityName, corpus.toString(), centerLat, centerLng, maxResults,
                    dietaryRestrictions, tastePreferences);
        } catch (Exception e) {
            log.warn("[GuideFood] LLM 抽取失败 {}: {}", cityName, e.getMessage());
            return List.of();
        }
    }

    private List<FoodRecommendation> parseWithLlm(String cityName, String corpus,
                                                 double lat, double lng, int maxResults,
                                                 List<String> dietaryRestrictions,
                                                 List<String> tastePreferences) throws Exception {
        int target = Math.max(3, maxResults);
        String constraints = FoodPreferenceHelper.toPromptConstraint(dietaryRestrictions, tastePreferences);
        String prompt = """
                你是美食攻略分析师。根据以下小红书笔记，提取「%s」值得去的餐厅或美食店。
                只输出 JSON 数组，不要 markdown：
                [{"name":"店名","category":"菜系/类型","priceRange":"人均价格","mealType":"lunch","reason":"推荐理由","mustTry":"必点菜"}]
                要求：去重、真实店名或美食地标、至少 %d 家；mealType 取 breakfast/lunch/dinner/snack 之一。
                
                用户饮食要求：
                %s
                
                笔记内容：
                %s
                """.formatted(cityName, target, constraints, corpus);

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
            if (i >= maxResults) break;
            double offsetLat = lat + (i % 5) * 0.004 * Math.cos(i * 0.9);
            double offsetLng = lng + (i % 5) * 0.004 * Math.sin(i * 0.9);
            String mealType = normalizeMeal(node.path("mealType").asText("lunch"));
            String reason = node.path("reason").asText("");
            String mustTry = node.path("mustTry").asText("");
            if (!mustTry.isBlank()) {
                reason = reason.isBlank() ? "必吃：" + mustTry : reason + "；必吃：" + mustTry;
            }

            result.add(FoodRecommendation.builder()
                    .name(node.path("name").asText("餐厅" + i))
                    .category(node.path("category").asText("当地美食"))
                    .rating(4.6)
                    .priceRange(node.path("priceRange").asText("价格待询"))
                    .distanceKm(0.5 + i * 0.3)
                    .address(cityName)
                    .businessStatus("营业中")
                    .openingHours("请参考小红书攻略或到店确认")
                    .mealType(mealType)
                    .lat(offsetLat)
                    .lng(offsetLng)
                    .recommendReason(reason.isBlank() ? "小红书攻略推荐" : reason)
                    .dataSource("xhs_guide")
                    .build());
            i++;
        }
        log.info("[GuideFood] 从笔记抽取 {} 家餐厅/美食", result.size());
        return result;
    }

    private static String normalizeMeal(String meal) {
        if (meal == null) return "lunch";
        String m = meal.trim().toLowerCase();
        return VALID_MEALS.contains(m) ? m : "lunch";
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}
