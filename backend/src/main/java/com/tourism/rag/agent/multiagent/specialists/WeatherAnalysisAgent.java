package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.dto.agent.ToolCallLog;
import com.tourism.rag.dto.agent.WeatherInfo;
import com.tourism.rag.service.AgentToolFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Specialist agent for weather analysis and outdoor suitability scoring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherAnalysisAgent extends Agent {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AgentToolFacade toolFacade;

    @Override
    public String agentId() {
        return "weather-analysis";
    }

    @Override
    public String displayName() {
        return "Weather Analysis Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.weatherAnalysis();
    }

    @Override
    public List<String> dependencies() {
        return List.of();
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityCode = ctx.getRequest().getCityCode();
        String cityName = ctx.getCityName();
        String start = ctx.getDates().get(0).format(DATE_FMT);
        String end = ctx.getDates().get(ctx.getTotalDays() - 1).format(DATE_FMT);

        List<WeatherInfo> weatherList = toolFacade.callWeather(
                cityCode, cityName, start, end, ctx.getToolCallLogs());

        boolean usedFallback = ctx.getToolCallLogs().stream()
                .filter(l -> "getWeather".equals(l.getToolName()))
                .anyMatch(ToolCallLog::isUsedFallback);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("weatherList", weatherList);
        payload.put("outdoorScoreByDate", buildOutdoorScores(weatherList));

        for (int i = 0; i < weatherList.size() && i < ctx.getTotalDays(); i++) {
            WeatherInfo w = weatherList.get(i);
            payload.put("day" + (i + 1) + "_tips", buildWeatherTips(w, ctx.getRequest().getPreferences()));
        }

        String summary = String.format("Retrieved %d days of weather data for %s (%s)",
                weatherList.size(), cityName, usedFallback ? "mock" : "live");

        return usedFallback
                ? AgentResult.fallback(agentId(), "Primary API unavailable; using historical climate data", payload)
                : AgentResult.success(agentId(), summary, payload);
    }

    private Map<String, Double> buildOutdoorScores(List<WeatherInfo> weatherList) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (WeatherInfo w : weatherList) {
            double score = w.isOutdoorFriendly() ? 0.8 : 0.3;
            if (w.getTempHigh() >= 35 || w.getTempLow() <= 0) score -= 0.2;
            if (w.getTempHigh() >= 20 && w.getTempHigh() <= 28) score += 0.2;
            scores.put(w.getDate(), Math.max(0, Math.min(1, score)));
        }
        return scores;
    }

    private List<String> buildWeatherTips(WeatherInfo weather, List<String> preferences) {
        List<String> tips = new ArrayList<>();
        if (!weather.isOutdoorFriendly()) {
            tips.add("天气: " + weather.getConditionText() + "，建议携带雨具，优先选择室内景点");
        }
        if (weather.getTempHigh() >= 30) tips.add("气温较高（最高" + weather.getTempHigh() + "℃），注意防晒补水");
        if (weather.getTempLow() <= 5) tips.add("早晚温差大，最低" + weather.getTempLow() + "℃，注意保暖");

        try {
            String ws = weather.getWindScale().replaceAll("[^\\d]", "").trim();
            if (!ws.isEmpty()) {
                int windScale = Integer.parseInt(ws.substring(0, 1));
                if (windScale >= 4) tips.add("风力较强（" + weather.getWindScale() + "），海边注意防风");
            }
        } catch (Exception ignored) {}

        if (preferences != null && preferences.contains("photography")) {
            tips.add("摄影建议：清晨和黄昏光线最佳，适合拍摄风景照");
        }
        if (tips.isEmpty()) tips.add("天气状况良好，适合全天户外出行！");
        return tips;
    }
}
