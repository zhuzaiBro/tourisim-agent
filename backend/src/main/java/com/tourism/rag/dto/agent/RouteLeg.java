package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteLeg {

    private String fromName;
    private String toName;
    private double distanceKm;
    private int durationMinutes;
    private String transportSuggestion;   // 步行 / 打车 / 公交 / 地铁
    private String instruction;           // 路线简要说明
}
