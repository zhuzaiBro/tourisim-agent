package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AccommodationSearchResult {

    private List<AccommodationRecommendation> accommodations;
    private AccommodationRecommendation primary;
    private List<String> tips;
    private String primaryProvider;
    private boolean usedFallback;

    @Builder.Default
    private List<String> providersUsed = new ArrayList<>();
}
