package com.tourism.rag.agent.guide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.food.FoodPreferenceHelper;
import com.tourism.rag.dto.agent.FoodRecommendation;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 对从小红书批量抽取的候选景点/餐厅做口碑分析：
 * 提取正负面信息 → 决定是否推荐 → 生成用户可见理由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XhsBatchOpinionFilter {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    @Value("${agent.xhs.opinion-filter-enabled:true}")
    private boolean enabled;

    @Value("${agent.xhs.min-recommend-confidence:0.45}")
    private double minConfidence;

    public List<PoiInfo> filterPois(String cityName, List<XiaohongshuNote> notes,
                                    List<PoiInfo> candidates, int maxKeep) {
        if (!enabled || candidates == null || candidates.isEmpty()) {
            return candidates != null ? candidates : List.of();
        }
        Map<String, XhsOpinionVerdict> verdicts = analyzeBatch(
                cityName, "景点", candidateNames(candidates), notes);
        return filterAndApplyPois(candidates, verdicts, maxKeep);
    }

    public List<FoodRecommendation> filterFoods(String cityName, List<XiaohongshuNote> notes,
                                                  List<FoodRecommendation> candidates, int maxKeep) {
        return filterFoods(cityName, notes, candidates, maxKeep, null, null);
    }

    public List<FoodRecommendation> filterFoods(String cityName, List<XiaohongshuNote> notes,
                                                  List<FoodRecommendation> candidates, int maxKeep,
                                                  List<String> dietaryRestrictions,
                                                  List<String> tastePreferences) {
        if (!enabled || candidates == null || candidates.isEmpty()) {
            return candidates != null ? candidates : List.of();
        }
        String constraints = FoodPreferenceHelper.toPromptConstraint(dietaryRestrictions, tastePreferences);
        Map<String, XhsOpinionVerdict> verdicts = analyzeBatch(
                cityName, "餐厅/美食", candidateNamesFood(candidates), notes, constraints);
        return filterAndApplyFoods(candidates, verdicts, maxKeep);
    }

    private Map<String, XhsOpinionVerdict> analyzeBatch(String cityName, String itemType,
                                                       List<String> names,
                                                       List<XiaohongshuNote> notes) {
        return analyzeBatch(cityName, itemType, names, notes, null);
    }

    private Map<String, XhsOpinionVerdict> analyzeBatch(String cityName, String itemType,
                                                       List<String> names,
                                                       List<XiaohongshuNote> notes,
                                                       String userConstraints) {
        if (names.isEmpty() || notes == null || notes.isEmpty()) {
            return Map.of();
        }
        String corpus = XhsNoteCorpus.build(notes, 15);
        String nameList = String.join("、", names);

        String constraintBlock = userConstraints != null && !userConstraints.isBlank()
                ? "\n用户饮食要求：\n" + userConstraints + "\n"
                : "";

        String prompt = """
                你是旅游口碑分析师。目的地：%s。根据下方小红书笔记，对候选%s逐一评估是否值得推荐给普通游客。
                
                候选列表：%s
                %s
                笔记语料：
                %s
                
                只输出 JSON 数组，不要 markdown：
                [{"name":"名称","recommend":true,"confidence":0.82,"positives":["亮点1"],"negatives":["注意点1"],"reason":"一句话推荐理由，可含正负面"}]
                
                规则：
                1. recommend=false：多篇笔记明确避雷、踩坑、无聊、不安全、严重差评；或与用户忌口/口味严重冲突
                2. recommend=true 但 negatives 非空：仍可推荐，须在 reason 中提示风险
                3. confidence 0-1，综合点赞收藏与口碑一致性
                4. 每个候选都要输出一条，name 与候选列表一致
                """.formatted(cityName, itemType, nameList, constraintBlock, corpus);

        try {
            String raw = chatLanguageModel.generate(prompt).trim();
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) {
                log.warn("[XhsOpinion] LLM 未返回有效 JSON");
                return Map.of();
            }
            JsonNode arr = objectMapper.readTree(raw.substring(start, end + 1));
            Map<String, XhsOpinionVerdict> map = new LinkedHashMap<>();
            for (JsonNode node : arr) {
                XhsOpinionVerdict v = parseVerdict(node);
                if (v.getName() != null && !v.getName().isBlank()) {
                    map.put(normalize(v.getName()), v);
                }
            }
            log.info("[XhsOpinion] {} 批量分析 {} 个候选，得到 {} 条口碑", cityName, names.size(), map.size());
            return map;
        } catch (Exception e) {
            log.warn("[XhsOpinion] 口碑分析失败 {}: {}", cityName, e.getMessage());
            return Map.of();
        }
    }

    private List<PoiInfo> filterAndApplyPois(List<PoiInfo> candidates,
                                              Map<String, XhsOpinionVerdict> verdicts,
                                              int maxKeep) {
        List<ScoredPoi> scored = new ArrayList<>();
        for (PoiInfo poi : candidates) {
            XhsOpinionVerdict v = verdicts.get(normalize(poi.getName()));
            if (v != null) {
                applyToPoi(poi, v);
                if (!v.isRecommend() && v.getConfidence() < minConfidence) {
                    log.info("[XhsOpinion] 过滤景点 {} — {}", poi.getName(), v.getReason());
                    continue;
                }
                scored.add(new ScoredPoi(poi, score(v)));
            } else {
                scored.add(new ScoredPoi(poi, 0.5));
            }
        }

        if (scored.isEmpty()) {
            log.warn("[XhsOpinion] 口碑过滤后无景点，保留原始列表前 {} 个", maxKeep);
            return candidates.stream().limit(maxKeep).collect(Collectors.toList());
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredPoi::score).reversed())
                .limit(maxKeep)
                .map(ScoredPoi::poi)
                .collect(Collectors.toList());
    }

    private List<FoodRecommendation> filterAndApplyFoods(List<FoodRecommendation> candidates,
                                                          Map<String, XhsOpinionVerdict> verdicts,
                                                          int maxKeep) {
        List<ScoredFood> scored = new ArrayList<>();
        for (FoodRecommendation food : candidates) {
            XhsOpinionVerdict v = verdicts.get(normalize(food.getName()));
            if (v != null) {
                applyToFood(food, v);
                if (!v.isRecommend() && v.getConfidence() < minConfidence) {
                    log.info("[XhsOpinion] 过滤餐厅 {} — {}", food.getName(), v.getReason());
                    continue;
                }
                scored.add(new ScoredFood(food, score(v)));
            } else {
                scored.add(new ScoredFood(food, 0.5));
            }
        }

        if (scored.isEmpty()) {
            log.warn("[XhsOpinion] 口碑过滤后无餐厅，保留原始列表前 {} 个", maxKeep);
            return candidates.stream().limit(maxKeep).collect(Collectors.toList());
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredFood::score).reversed())
                .limit(maxKeep)
                .map(ScoredFood::food)
                .collect(Collectors.toList());
    }

    private void applyToPoi(PoiInfo poi, XhsOpinionVerdict v) {
        poi.setDescription(formatOpinionReason(v));
        poi.setRating(Math.round((4.0 + v.getConfidence()) * 10.0) / 10.0);
        List<String> tags = new ArrayList<>(poi.getTags() != null ? poi.getTags() : List.of());
        if (!tags.contains("xhs_opinion")) tags.add("xhs_opinion");
        if (v.isRecommend() && v.getConfidence() >= 0.7) {
            tags.add("口碑推荐");
        } else if (!v.getNegatives().isEmpty()) {
            tags.add("注意避雷");
        }
        poi.setTags(tags);
    }

    private void applyToFood(FoodRecommendation food, XhsOpinionVerdict v) {
        food.setRecommendReason(formatOpinionReason(v));
        food.setRating(Math.round((4.0 + v.getConfidence()) * 10.0) / 10.0);
    }

    static String formatOpinionReason(XhsOpinionVerdict v) {
        if (v.getReason() != null && !v.getReason().isBlank()) {
            return v.getReason();
        }
        StringBuilder sb = new StringBuilder();
        if (!v.getPositives().isEmpty()) {
            sb.append("亮点：").append(String.join("、", v.getPositives()));
        }
        if (!v.getNegatives().isEmpty()) {
            if (!sb.isEmpty()) sb.append("；");
            sb.append("注意：").append(String.join("、", v.getNegatives()));
        }
        return sb.isEmpty() ? "小红书攻略提及" : sb.toString();
    }

    private XhsOpinionVerdict parseVerdict(JsonNode node) {
        List<String> positives = new ArrayList<>();
        List<String> negatives = new ArrayList<>();
        for (JsonNode p : node.path("positives")) {
            positives.add(p.asText());
        }
        for (JsonNode n : node.path("negatives")) {
            negatives.add(n.asText());
        }
        return XhsOpinionVerdict.builder()
                .name(node.path("name").asText())
                .recommend(node.path("recommend").asBoolean(true))
                .confidence(node.path("confidence").asDouble(0.6))
                .positives(positives)
                .negatives(negatives)
                .reason(node.path("reason").asText(""))
                .build();
    }

    private static double score(XhsOpinionVerdict v) {
        return v.isRecommend() ? v.getConfidence() : v.getConfidence() * 0.3;
    }

    private static List<String> candidateNames(List<PoiInfo> pois) {
        return pois.stream().map(PoiInfo::getName).filter(Objects::nonNull).toList();
    }

    private static List<String> candidateNamesFood(List<FoodRecommendation> foods) {
        return foods.stream().map(FoodRecommendation::getName).filter(Objects::nonNull).toList();
    }

    private static String normalize(String name) {
        return name != null ? name.trim().toLowerCase() : "";
    }

    private record ScoredPoi(PoiInfo poi, double score) {}
    private record ScoredFood(FoodRecommendation food, double score) {}
}
