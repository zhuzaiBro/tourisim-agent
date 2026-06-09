package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.util.GeoUtils;
import com.tourism.rag.dto.agent.AccommodationRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 高德地图住宿 POI 周边搜索（types=100000 住宿服务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeAccommodationProvider {

    private static final String AROUND_URL = "https://restapi.amap.com/v3/place/around";

    private final GaodeApiClient apiClient;

    public List<AccommodationRecommendation> search(String cityName,
                                                     double lat, double lng,
                                                     String accommodationType,
                                                     String budget,
                                                     double minRating,
                                                     int maxResults) {
        if (!apiClient.isConfigured()) {
            throw new AgentDataUnavailableException("高德 API Key 未配置，请设置 MAP_API_KEY");
        }
        try {
            String keywords = resolveKeywords(accommodationType, budget);
            JsonNode root = apiClient.get(AROUND_URL, Map.of(
                    "location", lng + "," + lat,
                    "radius", 5000,
                    "types", "100000",
                    "keywords", keywords,
                    "offset", Math.min(maxResults * 4, 25),
                    "sortrule", "rating",
                    "extensions", "all"
            ));

            if (!apiClient.isSuccess(root) || !root.has("pois")) {
                return List.of();
            }

            List<AccommodationRecommendation> result = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                JsonNode biz = poi.path("biz_ext");
                double rating = biz.path("rating").asDouble(4.0);
                if (rating < minRating) continue;

                String typeText = poi.path("type").asText("");
                if (!matchesBudget(typeText, biz, budget)) continue;

                String location = poi.path("location").asText("0,0");
                String[] parts = location.split(",");
                double pLng = Double.parseDouble(parts[0]);
                double pLat = Double.parseDouble(parts[1]);
                double dist = GeoUtils.haversineKm(lat, lng, pLat, pLng);

                String cost = biz.path("cost").asText("未知");
                String priceRange = cost.equals("未知") ? estimatePriceByBudget(budget) : cost + "元/晚";

                result.add(AccommodationRecommendation.builder()
                        .name(poi.path("name").asText())
                        .category(simplifyCategory(typeText))
                        .starLevel(extractStarLevel(typeText))
                        .rating(rating)
                        .priceRange(priceRange)
                        .distanceKm(Math.round(dist * 10.0) / 10.0)
                        .district(poi.path("business_area").asText(
                                poi.path("adname").asText("")))
                        .address(poi.path("address").asText())
                        .phone(poi.path("tel").asText(""))
                        .checkInTip("建议提前电话确认房态与入住时间")
                        .recommendReason("评分 " + rating + "，距行程中心约 " + dist + " 公里")
                        .lat(pLat).lng(pLng)
                        .dataSource("gaode_api")
                        .primaryPick(false)
                        .build());
            }

            return result.stream()
                    .sorted(Comparator
                            .comparingDouble(AccommodationRecommendation::getRating).reversed()
                            .thenComparingDouble(AccommodationRecommendation::getDistanceKm))
                    .limit(maxResults)
                    .collect(Collectors.toList());

        } catch (AgentDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentDataUnavailableException("高德住宿搜索失败: " + e.getMessage(), e);
        }
    }

    private static String resolveKeywords(String accommodationType, String budget) {
        if ("hostel".equals(accommodationType)) return "青年旅舍";
        if ("homestay".equals(accommodationType)) return "民宿";
        if ("high".equals(budget)) return "星级酒店";
        return "酒店";
    }

    private static boolean matchesBudget(String typeText, JsonNode biz, String budget) {
        String b = budget != null ? budget : "medium";
        String lower = typeText.toLowerCase();
        if ("low".equals(b)) {
            return !lower.contains("五星") && !lower.contains("豪华");
        }
        if ("high".equals(b)) {
            return lower.contains("五星") || lower.contains("四星")
                    || lower.contains("豪华") || lower.contains("精品");
        }
        return !lower.contains("五星");
    }

    private static String estimatePriceByBudget(String budget) {
        return switch (budget != null ? budget : "medium") {
            case "low" -> "150-280元/晚";
            case "high" -> "600-1200元/晚";
            default -> "280-500元/晚";
        };
    }

    private static String simplifyCategory(String typeText) {
        if (typeText.contains("民宿")) return "民宿";
        if (typeText.contains("青年")) return "青年旅舍";
        if (typeText.contains("公寓")) return "公寓酒店";
        return "酒店";
    }

    private static String extractStarLevel(String typeText) {
        if (typeText.contains("五星")) return "五星级";
        if (typeText.contains("四星")) return "四星级";
        if (typeText.contains("三星")) return "三星级";
        if (typeText.contains("经济")) return "经济型";
        return "";
    }
}
