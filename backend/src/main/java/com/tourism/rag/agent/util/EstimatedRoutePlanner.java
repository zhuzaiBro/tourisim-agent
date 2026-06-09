package com.tourism.rag.agent.util;

import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;
import com.tourism.rag.dto.agent.RouteLeg;

import java.util.ArrayList;
import java.util.List;

/**
 * 境外或高德路线不可用时，用 Haversine 距离估算路线（非 Mock 静态景点表）。
 */
public final class EstimatedRoutePlanner {

    private EstimatedRoutePlanner() {}

    public static RouteInfo plan(List<PoiInfo> pois, double startLat, double startLng, String transportMode) {
        if (pois == null || pois.isEmpty()) {
            return RouteInfo.builder().optimizedPois(List.of()).legs(List.of())
                    .totalDistanceKm(0).totalDurationMinutes(0)
                    .optimizationMethod("estimated_none").dataSource("estimated_route").build();
        }

        String mode = transportMode != null ? transportMode : "transit";
        double speedKmh = "driving".equals(mode) ? 35.0 : 4.5;

        List<PoiInfo> ordered = GeoUtils.nearestNeighborOrder(pois, startLat, startLng);
        List<RouteLeg> legs = new ArrayList<>();
        double curLat = startLat;
        double curLng = startLng;
        String fromName = "出发地/酒店";
        double totalDistKm = 0;
        int totalDurationMin = 0;

        for (PoiInfo poi : ordered) {
            double distKm = GeoUtils.haversineKm(curLat, curLng, poi.getLat(), poi.getLng());
            int durMin = Math.max(5, (int) Math.ceil(distKm / speedKmh * 60));
            totalDistKm += distKm;
            totalDurationMin += durMin;

            legs.add(RouteLeg.builder()
                    .fromName(fromName)
                    .toName(poi.getName())
                    .distanceKm(Math.round(distKm * 10.0) / 10.0)
                    .durationMinutes(durMin)
                    .transportSuggestion(suggestTransport(mode, durMin))
                    .instruction(fromName + " → " + poi.getName() + "（估算路线）")
                    .build());

            curLat = poi.getLat();
            curLng = poi.getLng();
            fromName = poi.getName();
        }

        return RouteInfo.builder()
                .optimizedPois(ordered)
                .legs(legs)
                .totalDistanceKm(Math.round(totalDistKm * 10.0) / 10.0)
                .totalDurationMinutes(totalDurationMin)
                .optimizationMethod("haversine_nearest_neighbor")
                .dataSource("estimated_route")
                .build();
    }

    private static String suggestTransport(String mode, int minutes) {
        if ("driving".equals(mode)) {
            return minutes <= 15 ? "打车/自驾" : "自驾约" + minutes + "分钟";
        }
        return minutes <= 15 ? "步行" : "步行约" + minutes + "分钟";
    }
}
