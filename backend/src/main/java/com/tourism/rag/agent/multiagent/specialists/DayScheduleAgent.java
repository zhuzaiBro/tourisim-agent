package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.agent.util.PoiIndoorClassifier;
import com.tourism.rag.dto.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for building daily time-slot activity schedules.
 *
 * <p>Stage 3 — depends on RouteOptimizationAgent, FoodRecommendationAgent, and WeatherAnalysisAgent.</p>
 */
@Slf4j
@Component
public class DayScheduleAgent extends Agent {

    @Override
    public String agentId() {
        return "day-scheduling";
    }

    @Override
    public String displayName() {
        return "Time Management Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.dayScheduling();
    }

    @Override
    public List<String> dependencies() {
        return List.of("route-optimization", "food-recommendation", "weather-analysis");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        int totalDays = ctx.getTotalDays();
        String transportMode = ctx.getRequest().getTransportMode();
        if (transportMode == null) transportMode = "transit";

        @SuppressWarnings("unchecked")
        List<WeatherInfo> weatherList = (List<WeatherInfo>)
                ctx.getResult("weather-analysis").getPayload().get("weatherList");

        Map<String, Object> payload = new LinkedHashMap<>();

        for (int day = 1; day <= totalDays; day++) {
            // Get route for this day
            RouteInfo route = (RouteInfo) ctx.getResult("route-optimization")
                    .getPayload().get("day" + day + "_route");
            RouteInfo altRoute = (RouteInfo) ctx.getResult("route-optimization")
                    .getPayload().get("day" + day + "_altRoute");

            // Get weather for this day
            WeatherInfo weather = weatherList != null && weatherList.size() >= day
                    ? weatherList.get(day - 1)
                    : createDefaultWeather();

            boolean compress = Boolean.TRUE.equals(ctx.getState("compressSchedule"));
            boolean expand = Boolean.TRUE.equals(ctx.getState("expandSchedule"));
            Integer maxVisit = (Integer) ctx.getState("maxVisitMinutes");
            Integer minVisit = (Integer) ctx.getState("minVisitMinutes");

            List<TimeSlotActivity> mainActivities = buildDailySchedule(
                    route, weather, transportMode, true, compress, expand, maxVisit, minVisit);
            payload.put("day" + day + "_mainActivities", mainActivities);

            List<TimeSlotActivity> altActivities = buildDailySchedule(
                    altRoute, weather, transportMode, false, compress, expand, maxVisit, minVisit);
            payload.put("day" + day + "_alternateActivities", altActivities);
        }

        String summary = String.format("Scheduled %d days with time-slot activities", totalDays);
        return AgentResult.success(agentId(), summary, payload);
    }

    /**
     * Build a full-day schedule: morning attractions → lunch → afternoon attractions
     * → evening free time → dinner → nighttime free time.
     */
    private List<TimeSlotActivity> buildDailySchedule(RouteInfo route, WeatherInfo weather,
                                                       String transport, boolean isMain,
                                                       boolean compress, boolean expand,
                                                       Integer maxVisit, Integer minVisit) {
        List<TimeSlotActivity> activities = new ArrayList<>();
        List<PoiInfo> pois = route != null ? route.getOptimizedPois() : List.of();
        if (pois.isEmpty()) return activities;

        int visitFloor = minVisit != null ? minVisit : 60;
        int visitCap = maxVisit != null ? maxVisit : (expand ? 150 : 90);

        int half = (pois.size() + 1) / 2;
        List<PoiInfo> morningPois = new ArrayList<>(pois.subList(0, half));
        List<PoiInfo> afternoonPois = pois.size() > half
                ? new ArrayList<>(pois.subList(half, pois.size()))
                : List.of();

        if (compress && !expand) {
            if (morningPois.size() > 2) morningPois = new ArrayList<>(morningPois.subList(0, 2));
            if (afternoonPois.size() > 1) afternoonPois = new ArrayList<>(afternoonPois.subList(0, 1));
        }

        int h = 9, m = 0;

        // Morning session (from 09:00)
        for (int i = 0; i < morningPois.size(); i++) {
            PoiInfo poi = morningPois.get(i);
            int travelMin = (i > 0 && route.getLegs() != null && route.getLegs().size() > i)
                    ? route.getLegs().get(i).getDurationMinutes() : 0;
            if (i > 0) { m += travelMin; h += m / 60; m %= 60; }

            String from = hm(h, m);
            int visitMin = resolveVisitMinutes(poi, compress, expand, visitFloor, visitCap);
            m += visitMin; h += m / 60; m %= 60;
            if (h >= 12) { h = 12; m = 0; }
            String to = hm(h, m);

            activities.add(TimeSlotActivity.builder()
                    .timeSlot(from + "-" + to)
                    .activity(poi.getName())
                    .type("attraction")
                    .poi(poi)
                    .durationMinutes(visitMin)
                    .transportFromPrev(i == 0 ? "从酒店出发" : transportSuggestion(travelMin, transport))
                    .transportMinutes(travelMin)
                    .estimatedCost(parseTicketCost(poi.getTicketPrice()))
                    .notes(buildNote(poi, weather, isMain))
                    .build());
        }

        // Lunch break (12:00-13:00)
        activities.add(TimeSlotActivity.builder()
                .timeSlot("12:00-13:00")
                .activity("午餐时间")
                .type("food")
                .durationMinutes(60)
                .estimatedCost(0)
                .notes("推荐就近品尝当地特色美食")
                .build());

        // Afternoon session (from 13:00)
        h = 13; m = 0;
        for (int i = 0; i < afternoonPois.size(); i++) {
            PoiInfo poi = afternoonPois.get(i);
            int absLeg = half + i;
            int travelMin = (i > 0 && route.getLegs() != null && route.getLegs().size() > absLeg)
                    ? route.getLegs().get(absLeg).getDurationMinutes() : 0;
            if (i > 0) { m += travelMin; h += m / 60; m %= 60; }

            String from = hm(h, m);
            int visitMin = resolveVisitMinutes(poi, compress, expand, visitFloor, visitCap);
            m += visitMin; h += m / 60; m %= 60;
            if (h >= 18) { h = 17; m = 30; }
            String to = hm(h, m);

            activities.add(TimeSlotActivity.builder()
                    .timeSlot(from + "-" + to)
                    .activity(poi.getName())
                    .type("attraction")
                    .poi(poi)
                    .durationMinutes(visitMin)
                    .transportFromPrev(i == 0 ? "午餐后出发" : transportSuggestion(travelMin, transport))
                    .transportMinutes(travelMin)
                    .estimatedCost(parseTicketCost(poi.getTicketPrice()))
                    .notes(buildNote(poi, weather, isMain))
                    .build());
        }

        if ((!compress || expand) && h < 17) {
            activities.add(TimeSlotActivity.builder()
                    .timeSlot(hm(h, m) + "-17:30")
                    .activity("自由探索 / 休闲购物")
                    .type("rest")
                    .durationMinutes(0)
                    .estimatedCost(0)
                    .notes("自由漫步周边街区，感受当地市井风情")
                    .build());
        }

        activities.add(TimeSlotActivity.builder()
                .timeSlot("18:00-19:30")
                .activity("晚餐时间")
                .type("food")
                .durationMinutes(compress ? 60 : 90)
                .estimatedCost(0)
                .notes("结束今日游览，享用晚餐")
                .build());

        if (!compress || expand) {
            activities.add(TimeSlotActivity.builder()
                    .timeSlot("19:30-21:00")
                    .activity("夜间自由时光")
                    .type("rest")
                    .durationMinutes(90)
                    .estimatedCost(0)
                    .notes("漫步夜市、欣赏夜景，或提前返回住所休息")
                    .build());
        }

        return activities;
    }

    private static int resolveVisitMinutes(PoiInfo poi, boolean compress, boolean expand,
                                           int visitFloor, int visitCap) {
        int raw = poi.getVisitDurationMinutes() > 0 ? poi.getVisitDurationMinutes() : 120;
        raw = Math.max(raw, visitFloor);
        if (expand) {
            return Math.min(Math.max(raw, 90), 150);
        }
        if (compress) {
            return Math.min(raw, visitCap);
        }
        return Math.min(raw, 150);
    }

    private WeatherInfo createDefaultWeather() {
        return WeatherInfo.builder()
                .date("2024-01-01")
                .condition("sunny")
                .conditionText("晴")
                .tempHigh(25).tempLow(15)
                .outdoorFriendly(true)
                .dataSource("默认")
                .build();
    }

    private static String hm(int hour, int min) {
        return String.format("%02d:%02d", Math.min(hour, 22), min % 60);
    }

    private static String transportSuggestion(int minutes, String mode) {
        if (minutes <= 10) return "步行";
        if ("driving".equals(mode)) return "打车/自驾";
        if (minutes <= 20) return "公交";
        return "公交/地铁";
    }

    private static double parseTicketCost(String ticketPrice) {
        if (ticketPrice == null || ticketPrice.contains("免费")) return 0;
        try {
            return Double.parseDouble(ticketPrice.replaceAll("[^\\d.]", "").split("\\.")[0]);
        } catch (Exception e) { return 50; }
    }

    private static String buildNote(PoiInfo poi, WeatherInfo weather, boolean isMain) {
        List<String> notes = new ArrayList<>();
        if (!isMain && PoiIndoorClassifier.isIndoor(poi)) {
            notes.add("雨天/高温备选：室内游览优选");
        }
        if (poi.getDescription() != null && !poi.getDescription().isBlank()
                && poi.getDataSource() != null && poi.getDataSource().contains("xhs")) {
            notes.add("攻略口碑：" + poi.getDescription());
        }
        if (poi.getTicketPrice() != null && !poi.getTicketPrice().contains("免费")) {
            notes.add("需购票：" + poi.getTicketPrice());
        }
        if (!weather.isOutdoorFriendly() && !poi.isIndoorVenue()) {
            notes.add("天气原因建议携带雨具");
        }
        if (poi.getOpeningHours() != null && !poi.getOpeningHours().contains("全天")) {
            notes.add("开放时间：" + poi.getOpeningHours());
        }
        return String.join("；", notes);
    }
}
