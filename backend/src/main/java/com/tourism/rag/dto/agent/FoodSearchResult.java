package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FoodSearchResult {

    private List<FoodRecommendation> foods;
    /** 主数据来源：gaode_api / xhs / llm_knowledge / gaode_api+xhs */
    private String primaryProvider;
    private boolean usedFallback;

    @Builder.Default
    private List<String> providersUsed = new ArrayList<>();
}
