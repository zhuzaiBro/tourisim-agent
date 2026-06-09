package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RouteInfo {

    private List<PoiInfo> optimizedPois;        // 最优顺序的景点列表
    private List<RouteLeg> legs;                // 每段路程详情
    private double totalDistanceKm;
    private int totalDurationMinutes;           // 纯交通时间（不含游览）
    private String optimizationMethod;          // nearest_neighbor / gaode_api
    private String dataSource;
}
