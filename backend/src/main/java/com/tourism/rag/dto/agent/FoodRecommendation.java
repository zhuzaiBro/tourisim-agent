package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoodRecommendation {

    private String name;
    private String category;            // 海鲜 / 火锅 / 本帮菜 / 小吃 等
    private double rating;              // 0-5
    private String priceRange;          // 如 "80-150元/人"
    private double distanceKm;          // 距当日主要景点距离（km）
    private String address;
    private String businessStatus;      // 营业中 / 休息中
    private String openingHours;
    private String phone;
    private String recommendReason;     // AI 推荐理由
    private String mealType;            // breakfast / lunch / dinner / snack
    private double lat;
    private double lng;
    private String dataSource;          // gaode_api / mock
}
