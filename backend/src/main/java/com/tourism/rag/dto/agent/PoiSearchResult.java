package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PoiSearchResult {

    private List<PoiInfo> pois;
    /** 主数据来源：gaode_api / rag / mock / gaode_api+rag */
    private String primaryProvider;
    private boolean usedFallback;
    private boolean ragEnriched;

    @Builder.Default
    private List<String> providersUsed = new ArrayList<>();
}
