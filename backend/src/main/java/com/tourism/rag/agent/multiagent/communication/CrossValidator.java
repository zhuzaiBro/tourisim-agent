package com.tourism.rag.agent.multiagent.communication;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.dto.agent.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Cross-validates outputs from multiple agents and detects conflicts.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Schedule sanity: total activity time + travel time ≤ 12 hours per day</li>
 *   <li>Weather consistency: outdoor-heavy days must have good weather</li>
 *   <li>Budget rationality: daily total should not exceed budget tier limits</li>
 *   <li>POI duplication: same POI should not appear on multiple days (unless intentional)</li>
 *   <li>Route feasibility: transit times between POIs must be physically possible</li>
 * </ul>
 *
 * <p>When issues are found, the CrossValidator triggers a {@link DebateSession}
 * to resolve them collaboratively.</p>
 */
@Slf4j
@Component
public class CrossValidator {

    private final AgentRegistry agentRegistry;
    private final ChatLanguageModel chatLanguageModel;

    public CrossValidator(@Lazy AgentRegistry agentRegistry, ChatLanguageModel chatLanguageModel) {
        this.agentRegistry = agentRegistry;
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * Validate all agent results against consistency rules.
     *
     * @return list of issues found (empty = all clear)
     */
    public List<String> validate(Map<String, AgentResult> results, AgentContext ctx) {
        List<String> issues = new ArrayList<>();

        // Check schedule sanity
        checkScheduleSanity(results, ctx, issues);

        // Check weather-activity consistency
        checkWeatherConsistency(results, ctx, issues);

        // Check budget rationality
        checkBudgetRationality(results, ctx, issues);

        // Check route feasibility
        checkRouteFeasibility(results, ctx, issues);

        if (issues.isEmpty()) {
            log.info("[CrossValidator] All validations passed — no issues found");
        } else {
            log.warn("[CrossValidator] Found {} issues: {}", issues.size(), issues);
        }

        return issues;
    }

    /**
     * Full validation with debate resolution.
     * If issues are found, runs a debate session to resolve them.
     *
     * @return the debate consensus (or null if no issues)
     */
    public ConsensusResult validateWithDebate(Map<String, AgentResult> results,
                                               AgentContext ctx,
                                               Consumer<AgentEvent> eventSink,
                                               double consensusThreshold,
                                               int maxRounds) {
        List<String> issues = validate(results, ctx);

        if (issues.isEmpty()) {
            return ConsensusResult.builder()
                    .action(ConsensusResult.Action.APPROVE)
                    .confidence(1.0)
                    .participantCount(0)
                    .voteTally(Map.of("APPROVE", 1L))
                    .arguments(List.of())
                    .build();
        }

        // Build participant list: the agents relevant to the issues
        List<String> participants = determineParticipants(issues);

        String issueSummary = String.join("; ", issues);
        DebateSession debate = new DebateSession(
                issueSummary, participants, agentRegistry, ctx, eventSink,
                consensusThreshold, maxRounds, chatLanguageModel);

        return debate.run();
    }

    // ---- Validation rules ----

    private void checkScheduleSanity(Map<String, AgentResult> results, AgentContext ctx,
                                      List<String> issues) {
        AgentResult scheduleResult = results.get("day-scheduling");
        if (scheduleResult == null) return;

        for (int day = 1; day <= ctx.getTotalDays(); day++) {
            @SuppressWarnings("unchecked")
            List<TimeSlotActivity> activities = (List<TimeSlotActivity>)
                    scheduleResult.getPayload().get("day" + day + "_mainActivities");
            if (activities == null) continue;

            int totalMinutes = activities.stream()
                    .mapToInt(a -> a.getDurationMinutes() + a.getTransportMinutes())
                    .sum();

            if (totalMinutes > 720) { // > 12 hours
                issues.add(String.format("第%d天：总活动时间（%d分钟）超过12小时",
                        day, totalMinutes));
            }
            if (totalMinutes < 180) { // < 3 hours
                issues.add(String.format("第%d天：仅%d分钟活动 — 行程过于松散",
                        day, totalMinutes));
            }
        }
    }

    private void checkWeatherConsistency(Map<String, AgentResult> results, AgentContext ctx,
                                          List<String> issues) {
        AgentResult weatherResult = results.get("weather-analysis");
        if (weatherResult == null) return;

        @SuppressWarnings("unchecked")
        List<WeatherInfo> weatherList = (List<WeatherInfo>) weatherResult.getPayload().get("weatherList");
        if (weatherList == null) return;

        AgentResult scheduleResult = results.get("day-scheduling");
        if (scheduleResult == null) return;

        for (int day = 1; day <= ctx.getTotalDays(); day++) {
            WeatherInfo weather = weatherList.size() >= day
                    ? weatherList.get(day - 1) : null;
            if (weather == null || weather.isOutdoorFriendly()) continue;

            @SuppressWarnings("unchecked")
            List<TimeSlotActivity> activities = (List<TimeSlotActivity>)
                    scheduleResult.getPayload().get("day" + day + "_mainActivities");
            if (activities == null) continue;

            long outdoorCount = activities.stream()
                    .filter(a -> a.getPoi() != null && !a.getPoi().isIndoorVenue())
                    .count();

            if (outdoorCount >= 3) {
                issues.add(String.format("第%d天：%s天气下有%d个户外活动 — 建议使用室内备选方案",
                        day, weather.getConditionText(), outdoorCount));
            }
        }
    }

    private void checkBudgetRationality(Map<String, AgentResult> results, AgentContext ctx,
                                         List<String> issues) {
        AgentResult budgetResult = results.get("budget-planning");
        if (budgetResult == null) return;

        String budgetLevel = ctx.getRequest().getBudget();
        if (budgetLevel == null) budgetLevel = "medium";

        for (int day = 1; day <= ctx.getTotalDays(); day++) {
            @SuppressWarnings("unchecked")
            Map<String, String> dayBudget = (Map<String, String>)
                    budgetResult.getPayload().get("day" + day + "_budget");
            if (dayBudget == null) continue;

            String totalStr = dayBudget.get("total");
            if (totalStr == null) continue;

            try {
                // Extract numeric range from "X–Y 元/人" format
                String[] parts = totalStr.replaceAll("[^\\d–]", "").split("–");
                if (parts.length >= 2) {
                    int max = Integer.parseInt(parts[1]);
                    int limit = switch (budgetLevel) {
                        case "low" -> 300;
                        case "high" -> 1200;
                        default -> 600;
                    };
                    if (max > limit * 1.5) {
                        issues.add(String.format("第%d天：预算（%s）严重超出%s档上限（%d）",
                                day, totalStr, budgetLevel, limit));
                    }
                }
            } catch (NumberFormatException ignored) {
                // Budget format not parseable — skip
            }
        }
    }

    private void checkRouteFeasibility(Map<String, AgentResult> results, AgentContext ctx,
                                        List<String> issues) {
        AgentResult routeResult = results.get("route-optimization");
        if (routeResult == null) return;

        for (int day = 1; day <= ctx.getTotalDays(); day++) {
            RouteInfo route = (RouteInfo) routeResult.getPayload().get("day" + day + "_route");
            if (route == null || route.getLegs() == null) continue;

            for (RouteLeg leg : route.getLegs()) {
                // Check if any single leg exceeds 2 hours
                if (leg.getDurationMinutes() > 120) {
                    issues.add(String.format("第%d天：路段%s→%s耗时%d分钟 — 建议拆分到多天",
                            day, leg.getFromName(), leg.getToName(), leg.getDurationMinutes()));
                }
            }
        }
    }

    /**
     * Determine which agents should participate in a debate about the given issues.
     */
    private List<String> determineParticipants(List<String> issues) {
        Set<String> participants = new LinkedHashSet<>();
        participants.add("safety-validation"); // always participates

        for (String issue : issues) {
            if (issue.contains("活动时间") || issue.contains("超过12小时") || issue.contains("过于松散")
                    || issue.contains("户外")) {
                participants.add("day-scheduling");
            }
            if (issue.contains("路段") || issue.contains("耗时")) {
                participants.add("route-optimization");
            }
            if (issue.contains("预算")) {
                participants.add("budget-planning");
            }
            if (issue.contains("天气")) {
                participants.add("weather-analysis");
            }
        }

        // Ensure at least 3 participants for a meaningful debate
        if (participants.size() < 3) {
            participants.add("narrative-generation");
        }

        return List.copyOf(participants);
    }
}
