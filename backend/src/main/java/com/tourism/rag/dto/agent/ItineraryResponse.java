package com.tourism.rag.dto.agent;

import com.tourism.rag.agent.multiagent.communication.ConsensusResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ItineraryResponse {

    private String itineraryId;             // UUID，用于 GET /api/agent/itinerary/{id}
    private String requestId;               // traceId，便于日志追踪
    private String cityCode;
    private String cityName;
    private String startDate;
    private String endDate;
    private int totalDays;
    private List<String> preferences;
    private String budget;
    private String transportMode;
    private String tripSummary;             // AI 生成的行程概述

    private List<DayPlan> days;

    /** 全程住宿推荐 */
    private List<AccommodationRecommendation> accommodations;
    private AccommodationRecommendation primaryAccommodation;
    private List<String> accommodationTips;

    /** 全程预算汇总 */
    private Map<String, String> totalBudget;

    /** 工具调用日志（可观测性） */
    private List<ToolCallLog> toolCallLogs;

    private String generatedAt;             // ISO-8601 生成时间
    private boolean hasRealWeatherData;
    private boolean hasRealPoiData;
    private boolean hasRealFoodData;
    private boolean hasRealAccommodationData;

    /** 多智能体辩论共识（仅 multi-agent 模式且开启辩论时有值） */
    private ConsensusResult consensusResult;
}
