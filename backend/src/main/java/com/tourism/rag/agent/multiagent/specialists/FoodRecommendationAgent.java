package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.dto.agent.FoodRecommendation;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.ToolCallLog;
import com.tourism.rag.service.AgentToolFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for culinary recommendations near daily POIs.
 */
@Component
@RequiredArgsConstructor
public class FoodRecommendationAgent extends Agent {

    private final AgentToolFacade toolFacade;

    @Override
    public String agentId() {
        return "food-recommendation";
    }

    @Override
    public String displayName() {
        return "Culinary Discovery Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.foodRecommendation();
    }

    @Override
    public List<String> dependencies() {
        return List.of("poi-discovery");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityCode = ctx.getRequest().getCityCode();
        String cityName = ctx.getCityName();
        List<String> preferences = ctx.getRequest().getPreferences();

        @SuppressWarnings("unchecked")
        List<PoiInfo> allPois = (List<PoiInfo>) ctx.getResult("poi-discovery")
                .getPayload().get("poiList");

        int totalDays = ctx.getTotalDays();
        int logsBefore = ctx.getToolCallLogs().size();
        Map<String, Object> payload = new LinkedHashMap<>();

        for (int day = 1; day <= totalDays; day++) {
            int poiIdx = (day - 1) % Math.max(1, allPois.size());
            PoiInfo refPoi = allPois.get(poiIdx);
            List<FoodRecommendation> meals = toolFacade.callFood(
                    cityCode, cityName, refPoi.getLat(), refPoi.getLng(), preferences,
                    ctx.getRequest().getDietaryRestrictions(),
                    ctx.getRequest().getTastePreferences(),
                    ctx.getToolCallLogs());
            payload.put("day" + day + "_foods", meals);
        }

        boolean usedFallback = ctx.getToolCallLogs().stream()
                .skip(logsBefore)
                .filter(l -> "recommendFood".equals(l.getToolName()))
                .anyMatch(ToolCallLog::isUsedFallback);

        String summary = String.format("Recommended dining options for %d days in %s %s",
                totalDays, cityName, usedFallback ? "[offline data]" : "[live data]");

        return usedFallback
                ? AgentResult.fallback(agentId(), "Food API unavailable; using curated offline data", payload)
                : AgentResult.success(agentId(), summary, payload);
    }
}
