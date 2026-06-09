package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.dto.agent.AccommodationSearchResult;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.ToolCallLog;
import com.tourism.rag.service.AgentToolFacade;
import com.tourism.rag.util.CityGeoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住宿安排专家：根据行程中心推荐酒店/民宿，并给出入住建议。
 */
@Component
@RequiredArgsConstructor
public class AccommodationRecommendationAgent extends Agent {

    private final AgentToolFacade toolFacade;
    private final CityGeoResolver cityGeoResolver;

    @Override
    public String agentId() {
        return "accommodation-recommendation";
    }

    @Override
    public String displayName() {
        return "Accommodation Planning Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.accommodationRecommendation();
    }

    @Override
    public List<String> dependencies() {
        return List.of("poi-discovery");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityCode = ctx.getRequest().getCityCode();
        String cityName = ctx.getCityName();
        String accommodationType = ctx.getRequest().getAccommodationType();
        if (accommodationType == null || accommodationType.isBlank()) {
            accommodationType = "hotel";
        }

        @SuppressWarnings("unchecked")
        List<PoiInfo> allPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("poiList");

        double[] center = resolveCenter(cityCode, cityName, allPois);
        int logsBefore = ctx.getToolCallLogs().size();

        AccommodationSearchResult search = toolFacade.callAccommodation(
                cityCode, cityName,
                center[0], center[1],
                accommodationType,
                ctx.getRequest().getBudget(),
                ctx.getRequest().getPreferences(),
                ctx.getToolCallLogs());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accommodations", search.getAccommodations());
        payload.put("primaryAccommodation", search.getPrimary());
        payload.put("accommodationTips", search.getTips());

        boolean usedFallback = ctx.getToolCallLogs().stream()
                .skip(logsBefore)
                .filter(l -> "recommendAccommodation".equals(l.getToolName()))
                .anyMatch(ToolCallLog::isUsedFallback);

        String summary = String.format("Planned stay options for %s (%d picks) %s",
                cityName,
                search.getAccommodations() != null ? search.getAccommodations().size() : 0,
                usedFallback ? "[AI enriched]" : "[live data]");

        return usedFallback
                ? AgentResult.fallback(agentId(), "Accommodation API limited; enriched with AI", payload)
                : AgentResult.success(agentId(), summary, payload);
    }

    private double[] resolveCenter(String cityCode, String cityName, List<PoiInfo> pois) {
        if (pois != null && !pois.isEmpty()) {
            double lat = pois.stream().mapToDouble(PoiInfo::getLat).average().orElse(0);
            double lng = pois.stream().mapToDouble(PoiInfo::getLng).average().orElse(0);
            if (lat != 0 && lng != 0) return new double[]{lat, lng};
        }
        return cityGeoResolver.resolve(cityCode, cityName);
    }
}
