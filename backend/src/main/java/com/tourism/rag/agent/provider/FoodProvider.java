package com.tourism.rag.agent.provider;

import com.tourism.rag.dto.agent.FoodRecommendation;

import java.util.List;

/**
 * 美食推荐数据提供者接口。
 * 实现：MockFoodProvider（兜底）、GaodeFoodProvider（高德 POI 美食搜索）。
 */
public interface FoodProvider {

    /**
     * 推荐餐厅。
     *
     * @param cityCode    城市代码
     * @param cityName    城市中文名
     * @param lat         参考坐标纬度（当日主景点附近）
     * @param lng         参考坐标经度
     * @param mealType    meal: breakfast / lunch / dinner
     * @param preferences 用户偏好
     * @param minRating   最低评分要求（默认 4.0）
     * @param maxResults  最多返回数量
     */
    List<FoodRecommendation> recommendFood(String cityCode, String cityName,
                                           double lat, double lng,
                                           String mealType, List<String> preferences,
                                           double minRating, int maxResults);

    String providerName();
}
