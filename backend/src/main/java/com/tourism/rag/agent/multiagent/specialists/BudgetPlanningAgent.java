package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import com.tourism.rag.dto.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Specialist agent for travel budget estimation and optimization.
 *
 * <p>Stage 3 — depends on DayScheduleAgent and FoodRecommendationAgent.</p>
 */
@Slf4j
@Component
public class BudgetPlanningAgent extends Agent {

    @Override
    public String agentId() {
        return "budget-planning";
    }

    @Override
    public String displayName() {
        return "Budget Planning Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.budgetPlanning();
    }

    @Override
    public List<String> dependencies() {
        return List.of("day-scheduling", "food-recommendation");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        String budgetLevel = ctx.getRequest().getBudget();
        if (budgetLevel == null) budgetLevel = "medium";

        int totalDays = ctx.getTotalDays();
        Map<String, Object> payload = new LinkedHashMap<>();

        for (int day = 1; day <= totalDays; day++) {
            Map<String, String> dayBudget = estimateDayBudget(
                    ctx, day, budgetLevel);
            payload.put("day" + day + "_budget", dayBudget);
        }

        // Trip total
        Map<String, String> tripTotal = aggregateTripBudget(ctx, budgetLevel);
        payload.put("tripTotal", tripTotal);

        String summary = String.format("Budget estimated for %d days at %s tier",
                totalDays, budgetLevel);
        return AgentResult.success(agentId(), summary, payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> estimateDayBudget(AgentContext ctx, int day, String budget) {
        // Get activities for this day
        AgentResult scheduleResult = ctx.getResult("day-scheduling");
        List<TimeSlotActivity> activities = (scheduleResult != null)
                ? (List<TimeSlotActivity>) scheduleResult.getPayload()
                    .get("day" + day + "_mainActivities")
                : List.of();

        // Sum attraction costs
        double attractionCost = activities != null
                ? activities.stream().mapToDouble(TimeSlotActivity::getEstimatedCost).sum()
                : 0;

        // Food cost per meal based on budget tier
        double foodPerMeal = switch (budget) {
            case "low" -> 20;
            case "high" -> 80;
            default -> 45;
        };
        double foodCost = foodPerMeal * 3; // 3 meals

        // Transport cost
        double transportCost = switch (budget) {
            case "low" -> 20;
            case "high" -> 80;
            default -> 40;
        };

        double total = attractionCost + foodCost + transportCost;
        double variance = total * 0.2;

        Map<String, String> b = new LinkedHashMap<>();
        b.put("attraction", rangeStr(attractionCost, variance));
        b.put("food", rangeStr(foodCost, variance * 0.5));
        b.put("transport", rangeStr(transportCost, variance * 0.5));
        b.put("total", rangeStr(total, variance));
        b.put("budgetTier", budget);
        return b;
    }

    private Map<String, String> aggregateTripBudget(AgentContext ctx, String budget) {
        int perDay = switch (budget) {
            case "low" -> 200;
            case "high" -> 800;
            default -> 450;
        };
        int days = ctx.getTotalDays();
        int min = perDay * days;
        int max = (int) (min * 1.3);

        int nights = Math.max(1, days - 1);
        int hotelPerNight = switch (budget) {
            case "low" -> 200;
            case "high" -> 900;
            default -> 400;
        };
        int hotelMin = hotelPerNight * nights;
        int hotelMax = (int) (hotelMin * 1.25);

        Map<String, String> total = new LinkedHashMap<>();
        total.put("attraction", "见每日行程");
        total.put("food", "见每日行程");
        total.put("transport", "见每日行程");
        total.put("accommodation", hotelMin + "–" + hotelMax + " 元（" + nights + " 晚）");
        total.put("total", min + "–" + max + " 元/人（不含住宿）");
        total.put("budgetTier", budget);

        // Add cost-saving tips based on budget tier
        if ("low".equals(budget)) {
            total.put("tip", "💡 经济出行建议：优先免费景点、选择公共交通、品尝街头小吃");
        } else if ("high".equals(budget)) {
            total.put("tip", "💎 高端体验：推荐精品酒店、米其林餐厅、包车服务，享受尊贵旅行体验");
        } else {
            total.put("tip", "🎯 舒适平衡：推荐高性价比酒店、热门餐厅提前预约、部分景点可选VIP通道");
        }

        return total;
    }

    private static String rangeStr(double base, double variance) {
        int low = (int) Math.max(0, Math.round(base - variance));
        int high = (int) Math.round(base + variance);
        if (low == 0 && high == 0) return "免费";
        if (low == high) return high + " 元";
        return low + "–" + high + " 元";
    }
}
