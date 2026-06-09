package com.tourism.rag.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.tourism.rag.agent.provider.GaodeApiClient;
import com.tourism.rag.agent.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 解析城市中心坐标（国内预设 + 海外常用城市 + 高德地理编码）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CityGeoResolver {

    private static final String GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";

    /** 境外常用城市（高德地理编码常误匹配到国内同名地名） */
    private static final Set<String> OVERSEAS_KEYS = Set.of(
            "singapore", "新加坡",
            "tokyo", "东京",
            "bangkok", "曼谷",
            "seoul", "首尔",
            "paris", "巴黎",
            "london", "伦敦",
            "kualalumpur", "kuala lumpur", "吉隆坡",
            "bali", "巴厘岛",
            "phuket", "普吉岛",
            "dubai", "迪拜",
            "sydney", "悉尼",
            "melbourne", "墨尔本",
            "newyork", "纽约",
            "losangeles", "洛杉矶"
    );

    private static final Map<String, double[]> KNOWN = Map.ofEntries(
            Map.entry("qingdao", new double[]{36.0671, 120.3826}),
            Map.entry("beijing", new double[]{39.9042, 116.4074}),
            Map.entry("shanghai", new double[]{31.2304, 121.4737}),
            Map.entry("xian", new double[]{34.3416, 108.9398}),
            Map.entry("chengdu", new double[]{30.5728, 104.0668}),
            Map.entry("hangzhou", new double[]{30.2741, 120.1551}),
            Map.entry("guilin", new double[]{25.2736, 110.2900}),
            Map.entry("xiamen", new double[]{24.4798, 118.0894}),
            Map.entry("singapore", new double[]{1.3521, 103.8198}),
            Map.entry("新加坡", new double[]{1.3521, 103.8198}),
            Map.entry("tokyo", new double[]{35.6762, 139.6503}),
            Map.entry("东京", new double[]{35.6762, 139.6503}),
            Map.entry("bangkok", new double[]{13.7563, 100.5018}),
            Map.entry("曼谷", new double[]{13.7563, 100.5018}),
            Map.entry("seoul", new double[]{37.5665, 126.9780}),
            Map.entry("首尔", new double[]{37.5665, 126.9780}),
            Map.entry("paris", new double[]{48.8566, 2.3522}),
            Map.entry("巴黎", new double[]{48.8566, 2.3522}),
            Map.entry("london", new double[]{51.5074, -0.1278}),
            Map.entry("伦敦", new double[]{51.5074, -0.1278}),
            Map.entry("kualalumpur", new double[]{3.1390, 101.6869}),
            Map.entry("kuala lumpur", new double[]{3.1390, 101.6869}),
            Map.entry("吉隆坡", new double[]{3.1390, 101.6869})
    );

    private final GaodeApiClient apiClient;

    public boolean isOverseas(String cityCode, String cityName) {
        if (cityName != null && isOverseasKey(cityName.trim())) {
            return true;
        }
        if (cityCode != null) {
            String codeKey = normalizeKey(cityCode);
            if (isOverseasKey(codeKey)) {
                return true;
            }
            if (CityNameResolver.isCustomDestination(cityCode)) {
                String customName = cityCode.substring(CityNameResolver.CUSTOM_PREFIX.length()).trim();
                return isOverseasKey(customName);
            }
        }
        return false;
    }

    public double[] resolve(String cityCode, String cityName) {
        if (cityCode != null) {
            String codeKey = normalizeKey(cityCode);
            double[] hit = KNOWN.get(codeKey);
            if (hit != null) return hit;
        }
        if (cityName != null && !cityName.isBlank()) {
            String name = cityName.trim();
            double[] byName = KNOWN.get(name);
            if (byName != null) return byName;
            if (!isOverseasKey(name)) {
                double[] geocoded = geocodeByName(name);
                if (geocoded != null) return geocoded;
            } else {
                log.warn("[CityGeo] 境外城市 {} 无预设坐标，使用默认参考点", name);
                return defaultOverseasFallback(name);
            }
        }
        return GeoUtils.getCityCenter(cityCode != null ? cityCode : "qingdao");
    }

    private double[] geocodeByName(String cityName) {
        if (!apiClient.isConfigured()) return null;
        try {
            JsonNode root = apiClient.get(GEOCODE_URL, Map.of("address", cityName));
            if (!apiClient.isSuccess(root) || !root.has("geocodes") || root.path("geocodes").isEmpty()) {
                return null;
            }
            String location = root.path("geocodes").get(0).path("location").asText("");
            if (location.isBlank() || !location.contains(",")) return null;
            String[] parts = location.split(",");
            double lng = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            log.info("[CityGeo] 地理编码 {} -> {},{}", cityName, lat, lng);
            return new double[]{lat, lng};
        } catch (Exception e) {
            log.warn("[CityGeo] 地理编码失败 {}: {}", cityName, e.getMessage());
            return null;
        }
    }

    private static String normalizeKey(String cityCode) {
        String key = cityCode.toLowerCase().trim();
        if (key.startsWith(CityNameResolver.CUSTOM_PREFIX)) {
            key = key.substring(CityNameResolver.CUSTOM_PREFIX.length()).trim();
        }
        return key;
    }

    private static boolean isOverseasKey(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.toLowerCase().trim();
        return OVERSEAS_KEYS.contains(key) || OVERSEAS_KEYS.contains(normalized);
    }

    private static double[] defaultOverseasFallback(String cityName) {
        log.info("[CityGeo] 境外城市 {} 使用东南亚参考坐标", cityName);
        return new double[]{3.1390, 101.6869};
    }
}
