package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.util.EstimatedRoutePlanner;
import com.tourism.rag.agent.util.GeoUtils;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;
import com.tourism.rag.dto.agent.RouteLeg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 高德地图 POI 搜索与路线规划提供者（禁用 Mock 兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeMapProvider implements MapProvider {

    private static final String POI_TEXT_URL = "https://restapi.amap.com/v3/place/text";
    private static final String WALKING_URL = "https://restapi.amap.com/v3/direction/walking";
    private static final String DRIVING_URL = "https://restapi.amap.com/v3/direction/driving";
    private static final String ATTRACTION_TYPES = "110000|141200|141300|140300|130300";

    private final GaodeApiClient apiClient;

    @Override
    public List<PoiInfo> searchPOI(String cityCode, String cityName, List<String> keywords,
                                    List<String> preferences, int maxResults) {
        if (!apiClient.isConfigured()) {
            throw new AgentDataUnavailableException("高德 API Key 未配置，请设置 MAP_API_KEY");
        }
        try {
            String kw = keywords != null && !keywords.isEmpty() ? String.join("|", keywords) : "景点";
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("keywords", kw);
            params.put("city", cityName);
            params.put("types", ATTRACTION_TYPES);
            params.put("offset", Math.min(maxResults, 25));
            params.put("extensions", "all");

            JsonNode root = apiClient.get(POI_TEXT_URL, params);
            List<PoiInfo> result = parsePois(root);

            if (result.size() < 6) {
                List<PoiInfo> broader = searchBroader(cityName, maxResults);
                result = mergeDistinct(result, broader);
            }

            if (result.isEmpty()) {
                log.warn("[Gaode] POI 搜索无结果，city={}, keywords={}", cityName, kw);
                return List.of();
            }
            log.info("[Gaode] 获取到 {} 个 POI", result.size());
            return result.stream().limit(maxResults).toList();

        } catch (AgentDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentDataUnavailableException("高德 POI 搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public RouteInfo planRoute(List<PoiInfo> pois, double startLat, double startLng, String transportMode) {
        if (!apiClient.isConfigured()) {
            throw new AgentDataUnavailableException("高德 API Key 未配置，请设置 MAP_API_KEY");
        }
        if (pois == null || pois.isEmpty()) {
            return RouteInfo.builder().optimizedPois(List.of()).legs(List.of())
                    .totalDistanceKm(0).totalDurationMinutes(0)
                    .optimizationMethod("none").dataSource("gaode_api").build();
        }

        try {
            return planRouteViaGaode(pois, startLat, startLng, transportMode);
        } catch (AgentDataUnavailableException e) {
            log.warn("[Gaode] 路线规划失败，改用 Haversine 估算: {}", e.getMessage());
            return EstimatedRoutePlanner.plan(pois, startLat, startLng, transportMode);
        }
    }

    private RouteInfo planRouteViaGaode(List<PoiInfo> pois, double startLat, double startLng, String transportMode) {
        String mode = transportMode != null ? transportMode : "transit";
        if ("transit".equals(mode)) {
            mode = "driving";
        }

        List<PoiInfo> orderedPois = GeoUtils.nearestNeighborOrder(pois, startLat, startLng);

        List<RouteLeg> legs = new ArrayList<>();
        double curLat = startLat;
        double curLng = startLng;
        String fromName = "出发地/酒店";
        double totalDistKm = 0;
        int totalDurationMin = 0;

        for (int i = 0; i < orderedPois.size(); i++) {
            PoiInfo poi = orderedPois.get(i);
            if (i > 0) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AgentDataUnavailableException("路线规划被中断", ie);
                }
            }
            if (poi.getLat() == 0 && poi.getLng() == 0) {
                throw new AgentDataUnavailableException("景点「" + poi.getName() + "」缺少坐标，无法调用高德路线规划");
            }

            JsonNode root = fetchDirection(curLat, curLng, poi.getLat(), poi.getLng(), mode);
            if (!apiClient.isSuccess(root)) {
                throw new AgentDataUnavailableException(
                        "高德路线规划失败: " + fromName + " → " + poi.getName());
            }

            JsonNode path = root.path("route").path("paths").path(0);
            if (path.isMissingNode()) {
                throw new AgentDataUnavailableException(
                        "高德路线无路径数据: " + fromName + " → " + poi.getName());
            }

            double distKm = path.path("distance").asDouble(0) / 1000.0;
            int durMin = (int) Math.ceil(path.path("duration").asDouble(0) / 60.0);
            totalDistKm += distKm;
            totalDurationMin += durMin;

            legs.add(RouteLeg.builder()
                    .fromName(fromName)
                    .toName(poi.getName())
                    .distanceKm(Math.round(distKm * 10.0) / 10.0)
                    .durationMinutes(durMin)
                    .transportSuggestion(suggestTransport(mode, durMin))
                    .instruction(path.path("steps").path(0).path("instruction").asText(
                            fromName + " → " + poi.getName()))
                    .build());

            curLat = poi.getLat();
            curLng = poi.getLng();
            fromName = poi.getName();
        }

        log.info("[Gaode] 路线规划完成 {} 段，总距 {}km，交通 {} 分钟",
                legs.size(), totalDistKm, totalDurationMin);

        return RouteInfo.builder()
                .optimizedPois(orderedPois)
                .legs(legs)
                .totalDistanceKm(Math.round(totalDistKm * 10.0) / 10.0)
                .totalDurationMinutes(totalDurationMin)
                .optimizationMethod("gaode_directions+nearest_neighbor")
                .dataSource("gaode_api")
                .build();
    }

    private List<PoiInfo> searchBroader(String cityName, int maxResults) {
        List<PoiInfo> merged = new ArrayList<>();
        for (String kw : List.of(cityName + " 景点", cityName + " 旅游", "地标")) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("keywords", kw);
            params.put("city", cityName);
            params.put("offset", Math.min(maxResults, 25));
            params.put("extensions", "all");
            try {
                JsonNode root = apiClient.get(POI_TEXT_URL, params);
                merged = mergeDistinct(merged, parsePois(root));
            } catch (Exception e) {
                log.debug("[Gaode] 扩展 POI 搜索失败 kw={}: {}", kw, e.getMessage());
            }
            if (merged.size() >= maxResults) break;
        }
        return merged;
    }

    private List<PoiInfo> parsePois(JsonNode root) {
        if (!apiClient.isSuccess(root) || !root.has("pois") || root.path("pois").isEmpty()) {
            return List.of();
        }
        List<PoiInfo> result = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) {
            String location = poi.path("location").asText("0,0");
            String[] parts = location.split(",");
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);

            JsonNode biz = poi.path("biz_ext");
            double rating = biz.path("rating").asDouble(4.5);
            String type = poi.path("type").asText("");
            boolean indoor = type.contains("博物馆") || type.contains("海洋") || type.contains("展览");

            result.add(PoiInfo.builder()
                    .id(poi.path("id").asText())
                    .name(poi.path("name").asText())
                    .category(type)
                    .address(poi.path("address").asText())
                    .lat(lat).lng(lng)
                    .rating(rating)
                    .openingHours(poi.path("opentime_week").asText("请提前确认"))
                    .ticketPrice("请咨询景区")
                    .visitDurationMinutes(120)
                    .indoorVenue(indoor)
                    .description(poi.path("name").asText() + "，" + poi.path("address").asText())
                    .dataSource("gaode_api")
                    .build());
        }
        return result;
    }

    private List<PoiInfo> mergeDistinct(List<PoiInfo> base, List<PoiInfo> extra) {
        Map<String, PoiInfo> map = new LinkedHashMap<>();
        for (PoiInfo p : base) {
            map.put(p.getName(), p);
        }
        for (PoiInfo p : extra) {
            map.putIfAbsent(p.getName(), p);
        }
        return new ArrayList<>(map.values());
    }

    private JsonNode fetchDirection(double fromLat, double fromLng, double toLat, double toLng, String mode) {
        String origin = fromLng + "," + fromLat;
        String destination = toLng + "," + toLat;
        Map<String, Object> params = Map.of("origin", origin, "destination", destination);
        String url = "driving".equals(mode) ? DRIVING_URL : WALKING_URL;
        return apiClient.get(url, params);
    }

    private String suggestTransport(String mode, int minutes) {
        if ("driving".equals(mode)) {
            return minutes <= 15 ? "打车/自驾" : "自驾";
        }
        return minutes <= 15 ? "步行" : "步行约" + minutes + "分钟";
    }

    @Override
    public String providerName() {
        return "gaode_api";
    }
}
