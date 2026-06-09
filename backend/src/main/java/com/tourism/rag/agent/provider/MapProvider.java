package com.tourism.rag.agent.provider;

import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteInfo;

import java.util.List;

/**
 * 地图/POI 数据提供者接口。
 * 实现：MockMapProvider（兜底）、GaodeMapProvider（高德地图 API）。
 */
public interface MapProvider {

    /**
     * 搜索 POI（景点/地点）。
     *
     * @param cityCode   城市代码
     * @param cityName   城市中文名
     * @param keywords   关键词，如 "海边 景点"
     * @param preferences 出行偏好列表，用于过滤类型
     * @param maxResults 最多返回数量
     */
    List<PoiInfo> searchPOI(String cityCode, String cityName, List<String> keywords,
                             List<String> preferences, int maxResults);

    /**
     * 规划多点路线（最近邻启发式 + 可选真实路由）。
     *
     * @param pois         待排序景点列表
     * @param startLat     出发点纬度（如酒店/市中心）
     * @param startLng     出发点经度
     * @param transportMode 出行方式
     */
    RouteInfo planRoute(List<PoiInfo> pois, double startLat, double startLng, String transportMode);

    String providerName();
}
