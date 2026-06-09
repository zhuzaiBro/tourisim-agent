package com.tourism.rag.agent.poi;

import com.tourism.rag.agent.provider.GaodeMapProvider;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;
import com.tourism.rag.dto.agent.ToolCallLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 按游玩天数批量调用高德规划每日路线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiDayRoutePlanner {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GaodeMapProvider gaodeMap;

    public Map<Integer, RouteInfo> planByDays(List<PoiInfo> allPois, int totalDays,
                                               double startLat, double startLng,
                                               String transportMode, List<ToolCallLog> logs) {
        int days = Math.max(1, totalDays);
        Map<Integer, List<PoiInfo>> byDay = groupPoisByDay(allPois, days);
        Map<Integer, RouteInfo> routes = new LinkedHashMap<>();

        for (int day = 1; day <= days; day++) {
            List<PoiInfo> dayPois = byDay.getOrDefault(day, List.of());
            if (dayPois.isEmpty()) {
                routes.put(day, emptyRoute());
                continue;
            }
            long t = System.currentTimeMillis();
            RouteInfo route = gaodeMap.planRoute(dayPois, startLat, startLng, transportMode);
            routes.put(day, route);
            appendLog(logs, route.getDataSource(), t);
            log.info("[RouteBatch] 第{}天 {} 个景点，总距 {}km，交通 {} 分钟",
                    day, dayPois.size(), route.getTotalDistanceKm(), route.getTotalDurationMinutes());
        }
        return routes;
    }

    private Map<Integer, List<PoiInfo>> groupPoisByDay(List<PoiInfo> allPois, int totalDays) {
        Map<Integer, List<PoiInfo>> buckets = new LinkedHashMap<>();
        for (int d = 1; d <= totalDays; d++) {
            buckets.put(d, new ArrayList<>());
        }

        List<PoiInfo> unassigned = new ArrayList<>();
        for (PoiInfo p : allPois) {
            Integer day = parseDayTag(p.getTags());
            if (day != null && day >= 1 && day <= totalDays) {
                buckets.get(day).add(p);
            } else {
                unassigned.add(p);
            }
        }

        int poisPerDay = Math.max(2, (int) Math.ceil((double) allPois.size() / totalDays));
        poisPerDay = Math.min(poisPerDay, 4);
        int idx = 0;
        for (PoiInfo p : unassigned) {
            int day = (idx % totalDays) + 1;
            if (buckets.get(day).size() < poisPerDay) {
                buckets.get(day).add(p);
                idx++;
            } else {
                // 桶满则找最少的
                int minDay = 1;
                int minSize = Integer.MAX_VALUE;
                for (int d = 1; d <= totalDays; d++) {
                    int sz = buckets.get(d).size();
                    if (sz < minSize) {
                        minSize = sz;
                        minDay = d;
                    }
                }
                buckets.get(minDay).add(p);
                idx++;
            }
        }
        return buckets;
    }

    private static Integer parseDayTag(List<String> tags) {
        if (tags == null) return null;
        for (String tag : tags) {
            if (tag == null) continue;
            if (tag.startsWith("day:")) {
                try {
                    return Integer.parseInt(tag.substring(4).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static RouteInfo emptyRoute() {
        return RouteInfo.builder()
                .optimizedPois(List.of())
                .legs(List.of())
                .totalDistanceKm(0)
                .totalDurationMinutes(0)
                .optimizationMethod("none")
                .dataSource("gaode_api")
                .build();
    }

    private void appendLog(List<ToolCallLog> logs, String provider, long startMs) {
        if (logs == null) return;
        logs.add(ToolCallLog.builder()
                .toolName("planRoute")
                .provider(provider)
                .startTime(LocalDateTime.now().format(ISO_FMT))
                .durationMs(System.currentTimeMillis() - startMs)
                .success(true)
                .usedFallback(false)
                .build());
    }
}
