package com.tourism.rag.agent.provider.xhs;

import com.tourism.rag.agent.food.FoodPreferenceHelper;
import com.tourism.rag.agent.guide.GuideFoodExtractor;
import com.tourism.rag.agent.guide.XhsBatchOpinionFilter;
import com.tourism.rag.dto.agent.FoodRecommendation;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import com.tourism.rag.util.CityGeoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过小红书笔记搜索为行程规划提供美食/餐厅推荐。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaohongshuFoodProvider {

    private final XiaohongshuApiClient apiClient;
    private final GuideFoodExtractor guideFoodExtractor;
    private final XhsBatchOpinionFilter opinionFilter;
    private final CityGeoResolver cityGeoResolver;

    /** 同城市多次 callFood（按天）时复用结果，避免重复搜笔记 */
    private final ConcurrentHashMap<String, List<FoodRecommendation>> cache = new ConcurrentHashMap<>();

    public boolean isConfigured() {
        return apiClient.isConfigured();
    }

    public List<FoodRecommendation> searchFood(String cityCode, String cityName,
                                                double centerLat, double centerLng,
                                                int maxResults) {
        return searchFood(cityCode, cityName, centerLat, centerLng, maxResults, null, null);
    }

    public List<FoodRecommendation> searchFood(String cityCode, String cityName,
                                                double centerLat, double centerLng,
                                                int maxResults,
                                                List<String> dietaryRestrictions,
                                                List<String> tastePreferences) {
        if (!isConfigured()) {
            return List.of();
        }

        String cacheKey = (cityCode != null ? cityCode : "") + ":"
                + (cityName != null ? cityName : "")
                + FoodPreferenceHelper.cacheKeySuffix(dietaryRestrictions, tastePreferences);
        List<FoodRecommendation> cached = cache.get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            log.debug("[XHS][Food] 命中缓存 city={} count={}", cityName, cached.size());
            return cached.stream().limit(maxResults).toList();
        }

        double[] center = cityGeoResolver.resolve(cityCode, cityName);
        double lat = centerLat != 0 ? centerLat : center[0];
        double lng = centerLng != 0 ? centerLng : center[1];

        List<XiaohongshuNote> notes = collectFoodNotes(cityName, tastePreferences, dietaryRestrictions);
        if (notes.isEmpty()) {
            log.info("[XHS][Food] 未搜到美食笔记 city={}", cityName);
            return List.of();
        }

        List<XiaohongshuNote> ranked = rankByEngagement(notes, Math.min(10, maxResults * 2));
        log.info("[XHS][Food] city={} 原始 {} 篇 → 精选 {} 篇用于抽取", cityName, notes.size(), ranked.size());

        int extractCount = maxResults + 3;
        List<FoodRecommendation> rawFoods = guideFoodExtractor.extractFromNotes(
                cityName, ranked, lat, lng, extractCount, dietaryRestrictions, tastePreferences);
        List<FoodRecommendation> foods = opinionFilter.filterFoods(
                cityName, ranked, rawFoods, maxResults, dietaryRestrictions, tastePreferences);
        if (!foods.isEmpty()) {
            cache.put(cacheKey, foods);
        }
        return foods;
    }

    private List<XiaohongshuNote> collectFoodNotes(String cityName,
                                                  List<String> tastePreferences,
                                                  List<String> dietaryRestrictions) {
        List<XiaohongshuNote> all = new ArrayList<>();
        Set<String> seenTitles = new LinkedHashSet<>();

        for (String q : FoodPreferenceHelper.buildXhsQueries(cityName, tastePreferences, dietaryRestrictions)) {
            List<XiaohongshuNote> batch = apiClient.searchNotes(q, 1, 20);
            log.info("[XHS][Food] keyword=\"{}\" 返回 {} 篇", q, batch.size());
            for (XiaohongshuNote n : batch) {
                if (seenTitles.add(n.getTitle())) {
                    all.add(n);
                }
            }
            if (all.size() >= 20) break;
        }
        return all;
    }

    private List<XiaohongshuNote> rankByEngagement(List<XiaohongshuNote> notes, int limit) {
        return notes.stream()
                .sorted(Comparator.comparingDouble(this::engagementScore).reversed())
                .limit(Math.max(limit, 1))
                .toList();
    }

    private double engagementScore(XiaohongshuNote note) {
        String text = (note.getTitle() + " " + nullToEmpty(note.getDescription())).toLowerCase();
        double base = Math.log10(Math.max(1, note.getLikes() + note.getCollects() + 1)) * 10;
        if (text.contains("美食") || text.contains("必吃") || text.contains("餐厅")) base += 8;
        if (text.contains("小吃") || text.contains("夜市") || text.contains("咖啡")) base += 5;
        return base;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
