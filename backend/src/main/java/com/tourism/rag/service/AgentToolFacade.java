package com.tourism.rag.service;

import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.accommodation.AccommodationSourceService;
import com.tourism.rag.agent.food.FoodSourceService;
import com.tourism.rag.agent.poi.PoiSourceService;
import com.tourism.rag.agent.weather.OverseasWeatherEstimator;
import com.tourism.rag.util.CityGeoResolver;
import com.tourism.rag.agent.provider.GaodeMapProvider;
import com.tourism.rag.agent.provider.GaodeWeatherProvider;
import com.tourism.rag.dto.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 单 Agent 与多 Agent 共享的外部工具调用层（天气 / POI / 路线 / 美食）。
 * 仅使用高德 API + RAG，禁用 Mock 兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolFacade {

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GaodeWeatherProvider gaodeWeather;
    private final PoiSourceService poiSourceService;
    private final GaodeMapProvider gaodeMap;
    private final FoodSourceService foodSourceService;
    private final AccommodationSourceService accommodationSourceService;
    private final CityGeoResolver cityGeoResolver;

    @Value("${agent.food.min-rating:4.0}")
    private double foodMinRating;

    @Value("${agent.food.max-results:5}")
    private int foodMaxResults;

    public List<WeatherInfo> callWeather(String cityCode, String cityName,
                                         String start, String end, List<ToolCallLog> logs) {
        long t = System.currentTimeMillis();
        String error = null;
        if (cityGeoResolver.isOverseas(cityCode, cityName)) {
            List<WeatherInfo> estimated = OverseasWeatherEstimator.estimate(
                    cityCode, cityName, start, end);
            appendLog(logs, "getWeather", "climate_estimate", t, true,
                    "境外目的地，使用气候估算");
            return estimated;
        }
        try {
            List<WeatherInfo> result = gaodeWeather.getWeather(cityCode, cityName, start, end);
            if (result.isEmpty()) {
                throw new AgentDataUnavailableException(
                        "高德天气无数据，请检查 MAP_API_KEY 或城市 adcode 是否支持: " + cityCode);
            }
            appendLog(logs, "getWeather", "gaode_weather", t, false, null);
            return result;
        } catch (AgentDataUnavailableException e) {
            if (OverseasWeatherEstimator.supports(cityCode, cityName)) {
                List<WeatherInfo> estimated = OverseasWeatherEstimator.estimate(
                        cityCode, cityName, start, end);
                appendLog(logs, "getWeather", "climate_estimate", t, true,
                        "高德不可用，使用气候估算");
                return estimated;
            }
            error = e.getMessage();
            appendLog(logs, "getWeather", "gaode_weather", t, false, error);
            throw e;
        } catch (Exception e) {
            error = e.getMessage();
            appendLog(logs, "getWeather", "gaode_weather", t, false, error);
            throw new AgentDataUnavailableException("高德天气调用失败: " + e.getMessage(), e);
        }
    }

    public List<PoiInfo> callPOI(String cityCode, String cityName,
                                  List<String> preferences, List<ToolCallLog> logs) {
        return callPOI(cityCode, cityName, preferences, 1, logs);
    }

    public List<PoiInfo> callPOI(String cityCode, String cityName,
                                  List<String> preferences, int totalDays, List<ToolCallLog> logs) {
        long t = System.currentTimeMillis();
        try {
            var search = poiSourceService.search(
                    cityCode, cityName, List.of("景点"), preferences, 15, Math.max(1, totalDays));
            appendLog(logs, "searchPOI", search.getPrimaryProvider(), t, false, null);

            if (search.isRagEnriched() || search.getProvidersUsed().contains("rag")) {
                logs.add(ToolCallLog.builder()
                        .toolName("enrichPOI").provider("rag")
                        .startTime(LocalDateTime.now().format(ISO_FMT))
                        .durationMs(0L)
                        .success(true).usedFallback(false).build());
            }
            return search.getPois();
        } catch (AgentDataUnavailableException e) {
            appendLog(logs, "searchPOI", "gaode_api", t, false, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[ToolFacade][searchPOI] 失败：{}", e.getMessage());
            appendLog(logs, "searchPOI", "gaode_api", t, false, e.getMessage());
            throw new AgentDataUnavailableException("POI 获取失败: " + e.getMessage(), e);
        }
    }

    public RouteInfo callRoute(List<PoiInfo> pois, double lat, double lng,
                                String transportMode, List<ToolCallLog> logs) {
        long t = System.currentTimeMillis();
        RouteInfo result = gaodeMap.planRoute(pois, lat, lng, transportMode);
        appendLog(logs, "planRoute", result.getDataSource(), t, false, null);
        return result;
    }

    public List<FoodRecommendation> callFood(String cityCode, String cityName,
                                              double lat, double lng,
                                              List<String> preferences, List<ToolCallLog> logs) {
        return callFood(cityCode, cityName, lat, lng, preferences, null, null, logs);
    }

    public List<FoodRecommendation> callFood(String cityCode, String cityName,
                                              double lat, double lng,
                                              List<String> preferences,
                                              List<String> dietaryRestrictions,
                                              List<String> tastePreferences,
                                              List<ToolCallLog> logs) {
        long t = System.currentTimeMillis();
        try {
            var search = foodSourceService.recommend(
                    cityCode, cityName, lat, lng, preferences,
                    dietaryRestrictions, tastePreferences,
                    foodMinRating, foodMaxResults);
            appendLog(logs, "recommendFood", search.getPrimaryProvider(), t,
                    search.isUsedFallback(), null);
            return search.getFoods();
        } catch (AgentDataUnavailableException e) {
            appendLog(logs, "recommendFood", "gaode_api", t, false, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[ToolFacade][recommendFood] 失败：{}", e.getMessage());
            appendLog(logs, "recommendFood", "gaode_api", t, false, e.getMessage());
            throw new AgentDataUnavailableException("美食获取失败: " + e.getMessage(), e);
        }
    }

    public AccommodationSearchResult callAccommodation(String cityCode, String cityName,
                                                        double lat, double lng,
                                                        String accommodationType,
                                                        String budget,
                                                        List<String> preferences,
                                                        List<ToolCallLog> logs) {
        long t = System.currentTimeMillis();
        try {
            var search = accommodationSourceService.recommend(
                    cityCode, cityName, lat, lng, accommodationType, budget, preferences);
            appendLog(logs, "recommendAccommodation", search.getPrimaryProvider(), t,
                    search.isUsedFallback(), null);
            return search;
        } catch (Exception e) {
            log.warn("[ToolFacade][recommendAccommodation] 失败：{}", e.getMessage());
            appendLog(logs, "recommendAccommodation", "gaode_api", t, false, e.getMessage());
            return AccommodationSearchResult.builder()
                    .accommodations(List.of())
                    .tips(List.of("住宿检索暂不可用，请自行预订"))
                    .primaryProvider("none")
                    .usedFallback(true)
                    .build();
        }
    }

    private void appendLog(List<ToolCallLog> logs, String toolName, String provider,
                           long startMs, boolean fallback, String error) {
        logs.add(ToolCallLog.builder()
                .toolName(toolName)
                .provider(provider)
                .startTime(LocalDateTime.now().format(ISO_FMT))
                .durationMs(System.currentTimeMillis() - startMs)
                .success(error == null)
                .usedFallback(fallback)
                .errorMessage(error)
                .build());
    }
}
