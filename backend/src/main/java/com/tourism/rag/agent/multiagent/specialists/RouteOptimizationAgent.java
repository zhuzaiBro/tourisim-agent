package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.agent.poi.MultiDayRoutePlanner;
import com.tourism.rag.agent.provider.GaodeMapProvider;
import com.tourism.rag.agent.util.PoiIndoorClassifier;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;
import com.tourism.rag.service.AgentToolFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for multi-stop route optimization.
 */
@Component
@RequiredArgsConstructor
public class RouteOptimizationAgent extends Agent {

    private final AgentToolFacade toolFacade;
    private final MultiDayRoutePlanner multiDayRoutePlanner;
    private final GaodeMapProvider gaodeMap;

    @Override
    public String agentId() {
        return "route-optimization";
    }

    @Override
    public String displayName() {
        return "Route Optimization Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.routeOptimization();
    }

    @Override
    public List<String> dependencies() {
        return List.of("poi-discovery", "weather-analysis");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String transportMode = ctx.getRequest().getTransportMode();
        if (transportMode == null) transportMode = "transit";

        @SuppressWarnings("unchecked")
        List<PoiInfo> allPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("poiList");
        @SuppressWarnings("unchecked")
        List<PoiInfo> indoorPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("indoorPois");
        if (indoorPois == null) {
            indoorPois = List.of();
        }
        indoorPois = supplementIndoorPois(
                ctx.getRequest().getCityCode(), ctx.getCityName(), indoorPois, allPois);

        double cityLat = ctx.getState("cityCenterLat") != null
                ? (double) ctx.getState("cityCenterLat") : 36.06;
        double cityLng = ctx.getState("cityCenterLng") != null
                ? (double) ctx.getState("cityCenterLng") : 120.38;

        int totalDays = ctx.getTotalDays();

        Map<Integer, RouteInfo> mainRoutes = multiDayRoutePlanner.planByDays(
                allPois, totalDays, cityLat, cityLng, transportMode, ctx.getToolCallLogs());

        Map<String, Object> payload = new LinkedHashMap<>();
        int poisPerDay = Math.max(2, Math.min(4, allPois.size() / Math.max(1, totalDays)));
        payload.put("poisPerDay", poisPerDay);

        for (int day = 1; day <= totalDays; day++) {
            RouteInfo mainRoute = mainRoutes.getOrDefault(day, emptyRoute());
            payload.put("day" + day + "_route", mainRoute);

            int dayPoiCount = mainRoute.getOptimizedPois() != null
                    ? mainRoute.getOptimizedPois().size() : poisPerDay;
            dayPoiCount = Math.max(2, Math.min(dayPoiCount, 4));
            List<PoiInfo> altRoutePois = pickAlternatePois(indoorPois, mainRoute.getOptimizedPois(), dayPoiCount);
            RouteInfo altRoute = toolFacade.callRoute(altRoutePois, cityLat, cityLng,
                    transportMode, ctx.getToolCallLogs());
            payload.put("day" + day + "_altRoute", altRoute);
        }

        String summary = String.format("Batch-planned %d-day routes (%d POIs total, %s mode)",
                totalDays, allPois.size(), transportMode);
        return AgentResult.success(agentId(), summary, payload);
    }

    private List<PoiInfo> supplementIndoorPois(String cityCode, String cityName,
                                              List<PoiInfo> indoor, List<PoiInfo> allPois) {
        List<PoiInfo> result = new ArrayList<>(indoor);
        if (result.size() >= 2) {
            return result;
        }
        try {
            List<PoiInfo> searched = gaodeMap.searchPOI(
                    cityCode, cityName,
                    List.of("博物馆", "科技馆", "美术馆", "商场"),
                    List.of(), 6);
            PoiIndoorClassifier.applyIndoorFlags(searched);
            for (PoiInfo p : searched) {
                if (!PoiIndoorClassifier.isIndoor(p)) continue;
                boolean dup = result.stream().anyMatch(x -> x.getName().equals(p.getName()));
                if (!dup) result.add(p);
            }
        } catch (Exception ignored) {
            // 高德不可用时用已有 POI 中的室内项
        }
        if (result.size() < 2) {
            for (PoiInfo p : PoiIndoorClassifier.filterIndoor(allPois)) {
                boolean dup = result.stream().anyMatch(x -> x.getName().equals(p.getName()));
                if (!dup) result.add(p);
            }
        }
        return result;
    }

    private static List<PoiInfo> pickAlternatePois(List<PoiInfo> indoorPois,
                                                    List<PoiInfo> mainPois,
                                                    int count) {
        List<PoiInfo> indoor = indoorPois != null ? indoorPois : List.of();
        if (!indoor.isEmpty()) {
            return new ArrayList<>(indoor.stream().limit(count).toList());
        }
        if (mainPois != null && !mainPois.isEmpty()) {
            return new ArrayList<>(mainPois);
        }
        return List.of();
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
}
