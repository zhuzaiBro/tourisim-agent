package com.tourism.rag.agent.multiagent.orchestration;

import com.tourism.rag.agent.multiagent.communication.ConsensusResult;
import com.tourism.rag.agent.multiagent.core.AgentContext;
import com.tourism.rag.agent.multiagent.core.AgentResult;
import com.tourism.rag.agent.multiagent.core.AgentStatus;
import com.tourism.rag.dto.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Merges the outputs of all specialist agents into the final {@link ItineraryResponse}.
 *
 * <p>This is the "synthesis" step that takes the distributed multi-agent results
 * and assembles the unified itinerary response expected by the frontend.</p>
 */
@Slf4j
@Component
public class ResultAggregator {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Aggregate all agent results into a single ItineraryResponse.
     */
    public ItineraryResponse aggregate(AgentContext ctx, String itineraryId,
                                        Map<String, AgentResult> allResults) {
        return aggregate(ctx, itineraryId, allResults, null);
    }

    public ItineraryResponse aggregate(AgentContext ctx, String itineraryId,
                                        Map<String, AgentResult> allResults,
                                        ConsensusResult consensus) {
        ItineraryRequest req = ctx.getRequest();

        @SuppressWarnings("unchecked")
        List<WeatherInfo> allWeather = getPayload(allResults, "weather-analysis", "weatherList");
        @SuppressWarnings("unchecked")
        List<PoiInfo> allPois = getPayload(allResults, "poi-discovery", "poiList");

        List<DayPlan> days = new ArrayList<>();
        int dayIndex = 0;
        for (var date : ctx.getDates()) {
            dayIndex++;
            DayPlan dayPlan = buildDayPlan(dayIndex, date, allWeather, allPois,
                    allResults, req);
            days.add(dayPlan);
        }

        String tripSummary = getPayload(allResults, "narrative-generation", "tripSummary");
        if (tripSummary == null) {
            tripSummary = "精心规划的" + ctx.getCityName() + ctx.getTotalDays()
                    + "日行程，融合自然风光、历史文化与地道美食。";
        }

        Map<String, String> totalBudget = buildTotalBudget(days, req.getBudget());

        List<ToolCallLog> toolLogs = buildToolCallLogs(ctx, allResults);

        @SuppressWarnings("unchecked")
        List<AccommodationRecommendation> accommodations =
                getPayload(allResults, "accommodation-recommendation", "accommodations");
        AccommodationRecommendation primary =
                getPayload(allResults, "accommodation-recommendation", "primaryAccommodation");
        @SuppressWarnings("unchecked")
        List<String> accommodationTips =
                getPayload(allResults, "accommodation-recommendation", "accommodationTips");

        return ItineraryResponse.builder()
                .itineraryId(itineraryId)
                .requestId(ctx.getRequestId())
                .cityCode(req.getCityCode())
                .cityName(ctx.getCityName())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .totalDays(ctx.getTotalDays())
                .preferences(req.getPreferences())
                .budget(req.getBudget())
                .transportMode(req.getTransportMode())
                .tripSummary(tripSummary)
                .days(days)
                .accommodations(accommodations != null ? accommodations : List.of())
                .primaryAccommodation(primary)
                .accommodationTips(accommodationTips != null ? accommodationTips : List.of())
                .totalBudget(totalBudget)
                .toolCallLogs(toolLogs)
                .generatedAt(LocalDateTime.now().format(ISO_FMT))
                .hasRealWeatherData(!isAllFallback(allResults, "weather-analysis"))
                .hasRealPoiData(!isAllFallback(allResults, "poi-discovery"))
                .hasRealFoodData(!isAllFallback(allResults, "food-recommendation"))
                .hasRealAccommodationData(!isAllFallback(allResults, "accommodation-recommendation"))
                .consensusResult(consensus)
                .build();
    }

    @SuppressWarnings("unchecked")
    private DayPlan buildDayPlan(int dayNumber, java.time.LocalDate date,
                                  List<WeatherInfo> allWeather,
                                  List<PoiInfo> allPois,
                                  Map<String, AgentResult> results,
                                  ItineraryRequest req) {

        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // Weather for this day
        WeatherInfo weather = findWeatherForDate(allWeather, dateStr);

        // Activities for this day
        List<TimeSlotActivity> mainActivities = getDayActivities(results, dayNumber, "main");
        List<TimeSlotActivity> altActivities = getDayActivities(results, dayNumber, "alternate");

        // Route
        RouteInfo route = getDayRoute(results, dayNumber, "main");
        RouteInfo altRoute = getDayRoute(results, dayNumber, "alternate");

        // Foods for this day
        List<FoodRecommendation> foods = getDayFoods(results, dayNumber);

        // Narrative
        String narrative = getDayNarrative(results, dayNumber);

        // Tips
        List<String> tips = getDayTips(results, dayNumber);

        // Budget
        Map<String, String> budget = getDayBudget(results, dayNumber);

        return DayPlan.builder()
                .date(dateStr)
                .dayNumber(dayNumber)
                .dayOfWeek(date.getDayOfWeek()
                        .getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE))
                .weather(weather)
                .mainPlanTitle(weather.isOutdoorFriendly()
                        ? "☀️ 晴天方案：" + req.getCityCode() + "经典游"
                        : "🌧️ 雨天方案：室内文化体验")
                .mainActivities(mainActivities)
                .alternatePlanTitle(weather.isOutdoorFriendly()
                        ? "🌂 雨天备选：博物馆与室内文化"
                        : "☀️ 好天气方案：户外精华打卡")
                .alternateActivities(altActivities)
                .route(route)
                .alternateRoute(altRoute)
                .foods(foods)
                .tips(tips)
                .narrative(narrative)
                .budget(budget)
                .build();
    }

    // ---- Helpers to extract data from agent results ----

    @SuppressWarnings("unchecked")
    private <T> T getPayload(Map<String, AgentResult> results, String agentId, String key) {
        AgentResult r = results.get(agentId);
        if (r == null || r.getPayload() == null) return null;
        return (T) r.getPayload().get(key);
    }

    private WeatherInfo findWeatherForDate(List<WeatherInfo> weatherList, String dateStr) {
        if (weatherList == null) return createDefaultWeather(dateStr);
        return weatherList.stream()
                .filter(w -> dateStr.equals(w.getDate()))
                .findFirst()
                .orElseGet(() -> createDefaultWeather(dateStr));
    }

    private WeatherInfo createDefaultWeather(String dateStr) {
        return WeatherInfo.builder()
                .date(dateStr)
                .condition("sunny")
                .conditionText("晴")
                .tempHigh(25)
                .tempLow(15)
                .windDir("南风")
                .windScale("2级")
                .humidity(50)
                .uvIndex("中等")
                .outdoorFriendly(true)
                .dataSource("默认")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<TimeSlotActivity> getDayActivities(Map<String, AgentResult> results,
                                                     int dayNumber, String planType) {
        AgentResult r = results.get("day-scheduling");
        if (r == null || r.getPayload() == null) return List.of();

        String key = "day" + dayNumber + "_" + planType + "Activities";
        Object val = r.getPayload().get(key);
        if (val instanceof List<?> list) {
            if (!list.isEmpty() && list.get(0) instanceof TimeSlotActivity) {
                return (List<TimeSlotActivity>) list;
            }
            return List.of();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private RouteInfo getDayRoute(Map<String, AgentResult> results, int dayNumber, String planType) {
        AgentResult r = results.get("route-optimization");
        if (r == null) return RouteInfo.builder().build();

        String key = "day" + dayNumber + (planType.equals("alternate") ? "_altRoute" : "_route");
        Object val = r.getPayload().get(key);
        if (val instanceof RouteInfo route) return route;
        return RouteInfo.builder().build();
    }

    @SuppressWarnings("unchecked")
    private List<FoodRecommendation> getDayFoods(Map<String, AgentResult> results, int dayNumber) {
        AgentResult r = results.get("food-recommendation");
        if (r == null || r.getPayload() == null) return List.of();

        String key = "day" + dayNumber + "_foods";
        Object val = r.getPayload().get(key);
        if (val instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof FoodRecommendation) {
            return (List<FoodRecommendation>) list;
        }
        return List.of();
    }

    private String getDayNarrative(Map<String, AgentResult> results, int dayNumber) {
        AgentResult r = results.get("narrative-generation");
        if (r == null || r.getPayload() == null)
            return "精心规划的行程，祝你旅途愉快！";

        String key = "day" + dayNumber + "_narrative";
        Object val = r.getPayload().get(key);
        if (val instanceof String s && !s.isBlank()) return s;

        // Fallback: use trip-level summary if day narrative missing
        String tripSummary = (String) r.getPayload().get("tripSummary");
        return tripSummary != null ? tripSummary : "精彩的一天等待着你！";
    }

    @SuppressWarnings("unchecked")
    private List<String> getDayTips(Map<String, AgentResult> results, int dayNumber) {
        AgentResult r = results.get("weather-analysis");
        if (r == null || r.getPayload() == null) return List.of("提前查看景区最新开放公告，祝旅途愉快！");

        String key = "day" + dayNumber + "_tips";
        Object val = r.getPayload().get(key);
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        // Also check safety-validation tips
        AgentResult sv = results.get("safety-validation");
        if (sv != null && sv.getPayload() != null) {
            Object svVal = sv.getPayload().get("day" + dayNumber + "_tips");
            if (svVal instanceof List<?> svList) {
                return svList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }
        }

        return List.of("祝旅途愉快！");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getDayBudget(Map<String, AgentResult> results, int dayNumber) {
        AgentResult r = results.get("budget-planning");
        if (r == null || r.getPayload() == null) {
            Map<String, String> b = new LinkedHashMap<>();
            b.put("total", "200–300 元/人");
            return b;
        }

        String key = "day" + dayNumber + "_budget";
        Object val = r.getPayload().get(key);
        if (val instanceof Map<?, ?> m) {
            Map<String, String> result = new LinkedHashMap<>();
            m.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
            return result;
        }
        Map<String, String> b = new LinkedHashMap<>();
        b.put("total", "200–300 元/人");
        return b;
    }

    private Map<String, String> buildTotalBudget(List<DayPlan> days, String budgetLevel) {
        int perDay = switch (budgetLevel != null ? budgetLevel : "medium") {
            case "low" -> 200;
            case "high" -> 800;
            default -> 450;
        };
        int min = perDay * days.size();
        int max = (int) (min * 1.3);
        Map<String, String> total = new LinkedHashMap<>();
        total.put("total", min + "–" + max + " 元/人");
        return total;
    }

    private List<ToolCallLog> buildToolCallLogs(AgentContext ctx, Map<String, AgentResult> results) {
        List<ToolCallLog> logs = new ArrayList<>(ctx.getToolCallLogs());
        if (!logs.isEmpty()) {
            return logs;
        }
        for (var entry : results.entrySet()) {
            AgentResult r = entry.getValue();
            logs.add(ToolCallLog.builder()
                    .toolName(entry.getKey())
                    .provider(r.isUsedFallback() ? "mock" : "multi-agent")
                    .startTime(r.getCompletedAt())
                    .durationMs(r.getDurationMs())
                    .success(r.getStatus() != AgentStatus.FAILED)
                    .usedFallback(r.isUsedFallback())
                    .errorMessage(r.getErrorMessage())
                    .build());
        }
        return logs;
    }

    private boolean isAllFallback(Map<String, AgentResult> results, String agentId) {
        AgentResult r = results.get(agentId);
        return r == null || r.isUsedFallback();
    }
}
