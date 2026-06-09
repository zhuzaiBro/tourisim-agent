package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolCallLog {

    private String toolName;            // getWeather / searchPOI / planRoute / recommendFood
    private String provider;            // hefeng_api / gaode_api / mock / rag
    private String startTime;           // ISO-8601
    private long durationMs;
    private boolean success;
    private boolean usedFallback;
    private String errorMessage;        // 失败时记录，成功时为 null
}
