package com.tourism.rag.agent.util;

import com.tourism.rag.dto.agent.PoiInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 地理计算工具（城市中心坐标、Haversine 距离、最近邻排序）。
 * 不含 Mock 业务数据，仅用于路线排序与坐标参考。
 */
public final class GeoUtils {

    private static final Map<String, double[]> CITY_CENTER = Map.ofEntries(
            Map.entry("qingdao", new double[]{36.0671, 120.3826}),
            Map.entry("beijing", new double[]{39.9042, 116.4074}),
            Map.entry("shanghai", new double[]{31.2304, 121.4737}),
            Map.entry("xian", new double[]{34.3416, 108.9398}),
            Map.entry("chengdu", new double[]{30.5728, 104.0668}),
            Map.entry("hangzhou", new double[]{30.2741, 120.1551}),
            Map.entry("guilin", new double[]{25.2736, 110.2900}),
            Map.entry("xiamen", new double[]{24.4798, 118.0894}),
            Map.entry("singapore", new double[]{1.3521, 103.8198}),
            Map.entry("新加坡", new double[]{1.3521, 103.8198})
    );

    private GeoUtils() {}

    public static double[] getCityCenter(String cityCode) {
        if (cityCode == null || cityCode.isBlank()) {
            return new double[]{36.0671, 120.3826};
        }
        String key = cityCode.toLowerCase().trim();
        if (key.startsWith("custom:")) {
            key = key.substring("custom:".length()).trim();
        }
        return CITY_CENTER.getOrDefault(key, new double[]{36.0671, 120.3826});
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /** 最近邻启发式：确定 POI 游览顺序（非 Mock 数据，仅排序算法） */
    public static List<PoiInfo> nearestNeighborOrder(List<PoiInfo> pois, double startLat, double startLng) {
        List<PoiInfo> unvisited = new ArrayList<>(pois);
        List<PoiInfo> ordered = new ArrayList<>();
        double curLat = startLat;
        double curLng = startLng;

        while (!unvisited.isEmpty()) {
            PoiInfo nearest = null;
            double minDist = Double.MAX_VALUE;
            for (PoiInfo p : unvisited) {
                double d = haversineKm(curLat, curLng, p.getLat(), p.getLng());
                if (d < minDist) {
                    minDist = d;
                    nearest = p;
                }
            }
            ordered.add(nearest);
            unvisited.remove(nearest);
            curLat = nearest.getLat();
            curLng = nearest.getLng();
        }
        return ordered;
    }
}
