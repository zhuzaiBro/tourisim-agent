package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DayPlan {

    private String date;                    // yyyy-MM-dd
    private int dayNumber;                  // 第几天
    private String dayOfWeek;               // 星期一 … 星期日
    private WeatherInfo weather;

    // ---- 晴天方案（户外优先） ----
    private String mainPlanTitle;           // 如 "晴天方案：海滨漫步+历史文化"
    private List<TimeSlotActivity> mainActivities;

    // ---- 雨天/恶劣天气备选方案（室内优先） ----
    private String alternatePlanTitle;      // 如 "雨天备选：博物馆+室内体验"
    private List<TimeSlotActivity> alternateActivities;

    private RouteInfo route;
    /** 雨天/室内备选路线 */
    private RouteInfo alternateRoute;

    private List<FoodRecommendation> foods; // 当天推荐餐厅（至少2-3家）
    private List<String> tips;              // 注意事项（防晒/带伞/预订等）
    private String narrative;               // AI 生成的当日行程叙述
    private Map<String, String> budget;     // 如 {attraction:"200-300", food:"150-200", total:"400-580"}
}
