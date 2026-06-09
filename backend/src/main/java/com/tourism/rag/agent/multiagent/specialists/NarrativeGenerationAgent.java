package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.dto.agent.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Specialist agent that generates engaging travel narratives using LLM.
 *
 * <p>Stage 4 — depends on DayScheduleAgent, WeatherAnalysisAgent, and FoodRecommendationAgent.
 * Runs before SafetyValidationAgent in the same stage.</p>
 */
@Slf4j
@Component
public class NarrativeGenerationAgent extends Agent {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ChatLanguageModel chatLanguageModel;

    public NarrativeGenerationAgent(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public String agentId() {
        return "narrative-generation";
    }

    @Override
    public String displayName() {
        return "Travel Storyteller";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.narrativeGeneration();
    }

    @Override
    public List<String> dependencies() {
        return List.of("day-scheduling", "weather-analysis", "food-recommendation");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String cityName = ctx.getCityName();
        List<String> preferences = ctx.getRequest().getPreferences();
        String prefStr = preferences != null && !preferences.isEmpty()
                ? String.join("/", preferences) : "休闲游";

        Map<String, Object> payload = new LinkedHashMap<>();

        // Generate per-day narratives
        for (int i = 0; i < ctx.getTotalDays(); i++) {
            LocalDate date = ctx.getDates().get(i);
            int dayNum = i + 1;

            WeatherInfo weather = getWeather(ctx, dayNum);

            @SuppressWarnings("unchecked")
            List<TimeSlotActivity> activities = getActivities(ctx, dayNum);
            String poiNames = activities != null
                    ? activities.stream()
                        .filter(a -> "attraction".equals(a.getType()))
                        .map(TimeSlotActivity::getActivity)
                        .collect(Collectors.joining("、"))
                    : "";

            @SuppressWarnings("unchecked")
            List<FoodRecommendation> foods = getFoods(ctx, dayNum);
            String foodNames = foods != null
                    ? foods.stream().limit(2)
                        .map(FoodRecommendation::getName)
                        .collect(Collectors.joining("、"))
                    : "";

            String narrative = generateDayNarrative(
                    date, weather, poiNames, foodNames, prefStr, cityName);
            payload.put("day" + dayNum + "_narrative", narrative);
        }

        // Generate trip summary
        String tripSummary = generateTripSummary(ctx, cityName);
        payload.put("tripSummary", tripSummary);

        String summary = "Generated narratives for " + ctx.getTotalDays()
                + " days and trip overview";
        return AgentResult.success(agentId(), summary, payload);
    }

    private String generateDayNarrative(LocalDate date, WeatherInfo weather,
                                         String poiNames, String foodNames,
                                         String preferences, String cityName) {
        try {
            String prompt = String.format("""
                    用一段100字以内的中文，描述这一天的旅游行程亮点。风格轻松活泼，突出当地特色。
                    城市：%s
                    日期：%s
                    天气：%s，%d~%d℃
                    游玩景点：%s
                    推荐美食：%s
                    出行偏好：%s
                    """,
                    cityName,
                    date.format(DATE_FMT),
                    weather != null ? weather.getConditionText() : "晴",
                    weather != null ? weather.getTempLow() : 15,
                    weather != null ? weather.getTempHigh() : 25,
                    poiNames.isEmpty() ? "待定" : poiNames,
                    foodNames.isEmpty() ? "当地特色" : foodNames,
                    preferences);

            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.debug("[NarrativeGenerationAgent] LLM failed, using template: {}", e.getMessage());
            return String.format("第%d天行程：%s，探访%s，品尝地道美食，度过充实的一天。",
                    date.getDayOfMonth(), cityName,
                    poiNames.isEmpty() ? "当地景点" : poiNames.substring(0, Math.min(20, poiNames.length())));
        }
    }

    private String generateTripSummary(AgentContext ctx, String cityName) {
        try {
            // Collect top highlights across all days
            List<String> highlights = new ArrayList<>();
            for (int day = 1; day <= ctx.getTotalDays(); day++) {
                List<TimeSlotActivity> activities = getActivities(ctx, day);
                if (activities != null) {
                    activities.stream()
                            .filter(a -> "attraction".equals(a.getType()))
                            .limit(2)
                            .map(TimeSlotActivity::getActivity)
                            .forEach(highlights::add);
                }
            }

            String highlightStr = highlights.isEmpty()
                    ? cityName + "各大景点"
                    : String.join("、", highlights.stream().limit(4).toList());

            String prompt = String.format("""
                    用80字以内的中文，写一段%d天旅行的总体介绍，突出亮点。
                    城市：%s
                    主要景点：%s
                    偏好：%s
                    """,
                    ctx.getTotalDays(), cityName, highlightStr,
                    ctx.getRequest().getPreferences() != null
                            ? String.join("/", ctx.getRequest().getPreferences())
                            : "综合");

            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.debug("[NarrativeGenerationAgent] Trip summary LLM failed: {}", e.getMessage());
            return String.format("精心规划的%s%d日行程，融合自然风光、历史文化与地道美食，为您带来难忘的旅游体验。",
                    cityName, ctx.getTotalDays());
        }
    }

    @SuppressWarnings("unchecked")
    private List<TimeSlotActivity> getActivities(AgentContext ctx, int day) {
        AgentResult r = ctx.getResult("day-scheduling");
        if (r == null) return List.of();
        return (List<TimeSlotActivity>) r.getPayload().get("day" + day + "_mainActivities");
    }

    @SuppressWarnings("unchecked")
    private List<FoodRecommendation> getFoods(AgentContext ctx, int day) {
        AgentResult r = ctx.getResult("food-recommendation");
        if (r == null) return List.of();
        return (List<FoodRecommendation>) r.getPayload().get("day" + day + "_foods");
    }

    @SuppressWarnings("unchecked")
    private WeatherInfo getWeather(AgentContext ctx, int day) {
        AgentResult r = ctx.getResult("weather-analysis");
        if (r == null) return null;
        List<WeatherInfo> weatherList = (List<WeatherInfo>) r.getPayload().get("weatherList");
        return weatherList != null && weatherList.size() >= day
                ? weatherList.get(day - 1) : null;
    }
}
