package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccommodationRecommendation {

    private String name;
    private String category;           // 星级酒店 / 经济型酒店 / 民宿 / 青年旅舍
    private String starLevel;          // 如 五星级、四星级
    private double rating;
    private String priceRange;         // 如 "280-450元/晚"
    private double distanceKm;
    private String district;           // 商圈或行政区
    private String address;
    private String phone;
    private String checkInTip;         // 入住提示
    private String recommendReason;
    private double lat;
    private double lng;
    private String dataSource;
    private boolean primaryPick;       // 是否首推
}
