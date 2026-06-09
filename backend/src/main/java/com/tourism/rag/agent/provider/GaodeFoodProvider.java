package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.util.GeoUtils;
import com.tourism.rag.dto.agent.FoodRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 高德地图美食 POI 周边搜索。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeFoodProvider implements FoodProvider {

    private static final String AROUND_URL = "https://restapi.amap.com/v3/place/around";

    private final GaodeApiClient apiClient;

    @Override
    public List<FoodRecommendation> recommendFood(String cityCode, String cityName,
                                                   double lat, double lng,
                                                   String mealType, List<String> preferences,
                                                   double minRating, int maxResults) {
        if (!apiClient.isConfigured()) {
            throw new AgentDataUnavailableException("高德 API Key 未配置，请设置 MAP_API_KEY");
        }
        try {
            JsonNode root = apiClient.get(AROUND_URL, Map.of(
                    "location", lng + "," + lat,
                    "radius", 3000,
                    "types", "050000",
                    "offset", Math.min(maxResults * 3, 25),
                    "sortrule", "rating",
                    "extensions", "all"
            ));

            if (!apiClient.isSuccess(root) || !root.has("pois")) {
                return List.of();
            }

            List<FoodRecommendation> result = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                JsonNode biz = poi.path("biz_ext");
                double rating = biz.path("rating").asDouble(4.0);
                if (rating < minRating) continue;

                String location = poi.path("location").asText("0,0");
                String[] parts = location.split(",");
                double pLng = Double.parseDouble(parts[0]);
                double pLat = Double.parseDouble(parts[1]);
                double dist = GeoUtils.haversineKm(lat, lng, pLat, pLng);

                String avgCost = biz.path("cost").asText("未知");
                String priceRange = avgCost.equals("未知") ? "价格待询" : avgCost + "元/人";

                result.add(FoodRecommendation.builder()
                        .name(poi.path("name").asText())
                        .category(poi.path("type").asText())
                        .rating(rating)
                        .priceRange(priceRange)
                        .distanceKm(Math.round(dist * 10.0) / 10.0)
                        .address(poi.path("address").asText())
                        .businessStatus(biz.path("open_time").asText("营业中"))
                        .openingHours(poi.path("opentime_week").asText("请提前确认"))
                        .phone(poi.path("tel").asText(""))
                        .mealType(mealType)
                        .lat(pLat).lng(pLng)
                        .recommendReason("评分 " + rating + "，距景点约 " + dist + " 公里")
                        .dataSource("gaode_api")
                        .build());
            }

            return result.stream()
                    .sorted(Comparator.comparingDouble(FoodRecommendation::getDistanceKm))
                    .limit(maxResults)
                    .collect(Collectors.toList());

        } catch (AgentDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentDataUnavailableException("高德美食搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "gaode_api";
    }
}
