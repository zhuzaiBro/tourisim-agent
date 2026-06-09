package com.tourism.rag.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ItinerarySummaryDto {
    private String id;
    private String cityCode;
    private String cityName;
    private String startDate;
    private String endDate;
    private int totalDays;
    private String tripSummary;
    private String createdAt;
}
