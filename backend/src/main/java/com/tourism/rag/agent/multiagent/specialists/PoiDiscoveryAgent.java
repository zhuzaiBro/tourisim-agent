package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.agent.util.PoiIndoorClassifier;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.util.CityGeoResolver;
import com.tourism.rag.dto.agent.ToolCallLog;
import com.tourism.rag.service.AgentToolFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for POI (attraction) discovery and ranking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoiDiscoveryAgent extends Agent {

    private final AgentToolFacade toolFacade;
    private final CityGeoResolver cityGeoResolver;

    @Override
    public String agentId() {
        return "poi-discovery";
    }

    @Override
    public String displayName() {
        return "Attraction Discovery Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.poiDiscovery();
    }

    @Override
    public List<String> dependencies() {
        return List.of();
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityCode = ctx.getRequest().getCityCode();
        String cityName = ctx.getCityName();
        List<String> preferences = ctx.getRequest().getPreferences();

        int logsBefore = ctx.getToolCallLogs().size();
        List<PoiInfo> pois = toolFacade.callPOI(
                cityCode, cityName, preferences, ctx.getTotalDays(), ctx.getToolCallLogs());
        boolean usedFallback = ctx.getToolCallLogs().stream()
                .skip(logsBefore)
                .filter(l -> "searchPOI".equals(l.getToolName()))
                .anyMatch(ToolCallLog::isUsedFallback);

        String primaryProvider = ctx.getToolCallLogs().stream()
                .filter(l -> "searchPOI".equals(l.getToolName()))
                .map(ToolCallLog::getProvider)
                .reduce((a, b) -> b)
                .orElse("gaode_api");
        boolean ragEnriched = ctx.getToolCallLogs().stream()
                .anyMatch(l -> "enrichPOI".equals(l.getToolName()));

        List<PoiInfo> ranked = rankByPreferences(pois, preferences);
        PoiIndoorClassifier.applyIndoorFlags(ranked);
        List<PoiInfo> outdoor = ranked.stream().filter(p -> !PoiIndoorClassifier.isIndoor(p)).toList();
        List<PoiInfo> indoor = PoiIndoorClassifier.filterIndoor(ranked);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poiList", ranked);
        payload.put("outdoorPois", outdoor);
        payload.put("indoorPois", indoor);
        payload.put("totalCount", ranked.size());
        payload.put("primaryProvider", primaryProvider);
        payload.put("ragEnriched", ragEnriched);
        double[] center = cityGeoResolver.resolve(cityCode, cityName);
        payload.put("cityCenter", center);
        ctx.putState("cityCenterLat", center[0]);
        ctx.putState("cityCenterLng", center[1]);

        String summary = String.format("Discovered %d attractions in %s (%d outdoor, %d indoor) %s",
                ranked.size(), cityName, outdoor.size(), indoor.size(),
                usedFallback ? "[offline data]" : "[live data]");

        return usedFallback
                ? AgentResult.fallback(agentId(), "POI API unavailable; using curated offline data", payload)
                : AgentResult.success(agentId(), summary, payload);
    }

    private List<PoiInfo> rankByPreferences(List<PoiInfo> pois, List<String> preferences) {
        if (preferences == null || preferences.isEmpty()) return pois;
        List<PoiInfo> sorted = new ArrayList<>(pois);
        sorted.sort((a, b) -> Integer.compare(matchScore(b, preferences), matchScore(a, preferences)));
        return sorted;
    }

    private int matchScore(PoiInfo poi, List<String> preferences) {
        int score = 0;
        if (poi.getTags() == null) return score;
        for (String tag : poi.getTags()) {
            for (String pref : preferences) {
                if (tag.toLowerCase().contains(pref.toLowerCase())) score += 2;
            }
        }
        return score;
    }
}
