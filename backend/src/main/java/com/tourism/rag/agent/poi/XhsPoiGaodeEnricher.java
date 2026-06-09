package com.tourism.rag.agent.poi;

import com.tourism.rag.agent.provider.GaodeMapProvider;
import com.tourism.rag.agent.util.GeoUtils;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.util.CityGeoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 将小红书抽取的景点批量对齐高德 POI 坐标（境内有效；境外距离过远则保留原坐标）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XhsPoiGaodeEnricher {

    private static final double MAX_GEO_OFFSET_KM = 80;

    private final GaodeMapProvider gaodeMap;
    private final CityGeoResolver cityGeoResolver;

    public List<PoiInfo> enrichBatch(String cityCode, String cityName, List<PoiInfo> pois) {
        if (pois == null || pois.isEmpty()) return List.of();

        if (cityGeoResolver.isOverseas(cityCode, cityName)) {
            log.info("[XhsGaode] 境外目的地 {}，跳过高德坐标补全（保留 XHS/LLM 坐标）", cityName);
            return pois;
        }

        double[] center = cityGeoResolver.resolve(cityCode, cityName);
        List<PoiInfo> result = new ArrayList<>();

        for (PoiInfo poi : pois) {
            PoiInfo copy = copyPoi(poi);
            if (isXhsSource(copy.getDataSource())) {
                resolveViaGaode(cityCode, cityName, center, copy);
            }
            result.add(copy);
        }
        log.info("[XhsGaode] 批量补全坐标完成 {} 个景点", result.size());
        return result;
    }

    private void resolveViaGaode(String cityCode, String cityName, double[] center, PoiInfo poi) {
        try {
            Thread.sleep(220);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            List<PoiInfo> hits = gaodeMap.searchPOI(
                    cityCode, cityName, List.of(poi.getName()), List.of(), 3);
            if (hits.isEmpty()) {
                log.debug("[XhsGaode] 未命中高德 POI: {}", poi.getName());
                return;
            }
            PoiInfo best = hits.get(0);
            double dist = GeoUtils.haversineKm(center[0], center[1], best.getLat(), best.getLng());
            if (dist > MAX_GEO_OFFSET_KM) {
                log.debug("[XhsGaode] 跳过偏离过远的匹配 {} -> {}km", poi.getName(), dist);
                return;
            }
            poi.setLat(best.getLat());
            poi.setLng(best.getLng());
            if (best.getAddress() != null && !best.getAddress().isBlank()) {
                poi.setAddress(best.getAddress());
            }
            poi.setDataSource("xhs_guide+gaode_api");
            log.info("[XhsGaode] {} -> {},{} ({})", poi.getName(), best.getLat(), best.getLng(), best.getAddress());
        } catch (Exception e) {
            log.debug("[XhsGaode] 补全失败 {}: {}", poi.getName(), e.getMessage());
        }
    }

    private static boolean isXhsSource(String ds) {
        return ds != null && ds.contains("xhs");
    }

    private PoiInfo copyPoi(PoiInfo src) {
        return PoiInfo.builder()
                .id(src.getId())
                .name(src.getName())
                .category(src.getCategory())
                .address(src.getAddress())
                .lat(src.getLat())
                .lng(src.getLng())
                .rating(src.getRating())
                .openingHours(src.getOpeningHours())
                .ticketPrice(src.getTicketPrice())
                .visitDurationMinutes(src.getVisitDurationMinutes())
                .indoorVenue(src.isIndoorVenue())
                .tags(src.getTags())
                .description(src.getDescription())
                .dataSource(src.getDataSource())
                .build();
    }
}
