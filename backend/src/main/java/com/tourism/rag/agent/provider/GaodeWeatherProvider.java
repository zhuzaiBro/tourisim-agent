package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.dto.agent.WeatherInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 高德天气预报提供者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeWeatherProvider implements WeatherProvider {

    private static final String WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final GaodeApiClient apiClient;

    private static final Map<String, String> CITY_ADCODE = Map.ofEntries(
            Map.entry("qingdao", "370200"),
            Map.entry("beijing", "110100"),
            Map.entry("shanghai", "310100"),
            Map.entry("xian", "610100"),
            Map.entry("chengdu", "510100"),
            Map.entry("guilin", "450300"),
            Map.entry("hangzhou", "330100"),
            Map.entry("suzhou", "320500"),
            Map.entry("shenzhen", "440300"),
            Map.entry("guangzhou", "440100")
    );

    private static String textToCondition(String text) {
        if (text == null) return "partly_cloudy";
        if (text.contains("雷")) return "thunderstorm";
        if (text.contains("暴雨") || text.contains("大雨")) return "heavy_rain";
        if (text.contains("中雨")) return "moderate_rain";
        if (text.contains("雨")) return "light_rain";
        if (text.contains("雪")) return "snow";
        if (text.contains("雾") || text.contains("霾")) return "fog";
        if (text.contains("阴")) return "cloudy";
        if (text.contains("多云")) return "partly_cloudy";
        if (text.contains("晴")) return "sunny";
        return "partly_cloudy";
    }

    @Override
    public List<WeatherInfo> getWeather(String cityCode, String cityName,
                                        String startDate, String endDate) {
        if (!apiClient.isConfigured()) {
            throw new AgentDataUnavailableException("高德 API Key 未配置，请设置 MAP_API_KEY");
        }

        String adcode = CITY_ADCODE.get(cityCode.toLowerCase());
        if (adcode == null) {
            throw new AgentDataUnavailableException("未找到城市 adcode，暂不支持天气查询: " + cityCode);
        }

        try {
            JsonNode root = apiClient.get(WEATHER_URL, Map.of(
                    "city", adcode,
                    "extensions", "all"
            ));
            if (!apiClient.isSuccess(root)) {
                throw new AgentDataUnavailableException("高德天气 API 返回失败: " + cityCode);
            }

            JsonNode casts = root.path("forecasts").get(0).path("casts");
            if (casts == null || casts.isEmpty()) {
                throw new AgentDataUnavailableException("高德天气预报为空: " + cityCode);
            }

            LocalDate start = LocalDate.parse(startDate, FMT);
            LocalDate end = LocalDate.parse(endDate, FMT);

            List<WeatherInfo> result = new ArrayList<>();
            for (JsonNode cast : casts) {
                String dateStr = cast.path("date").asText();
                LocalDate date;
                try {
                    date = LocalDate.parse(dateStr, FMT);
                } catch (Exception e) {
                    continue;
                }
                if (date.isBefore(start) || date.isAfter(end)) continue;

                String conditionText = cast.path("dayweather").asText("晴");
                String condition = textToCondition(conditionText);
                boolean outdoor = !condition.contains("rain")
                        && !condition.contains("snow")
                        && !condition.contains("thunderstorm")
                        && !condition.contains("fog");

                int tempHigh = parseInt(cast.path("daytemp").asText("20"));
                int tempLow = parseInt(cast.path("nighttemp").asText("12"));
                String daypower = cast.path("daypower").asText("3");
                String windScale = daypower.contains("-") ? daypower.split("-")[0] : daypower;

                result.add(WeatherInfo.builder()
                        .date(dateStr)
                        .condition(condition)
                        .conditionText(conditionText)
                        .tempHigh(tempHigh)
                        .tempLow(tempLow)
                        .windDir(cast.path("daywind").asText("东南"))
                        .windScale(windScale)
                        .humidity(65)
                        .uvIndex("中等")
                        .precipitation(condition.contains("rain") ? "有降水" : "无降水")
                        .outdoorFriendly(outdoor)
                        .dataSource("gaode_weather")
                        .build());
            }
            log.info("[GaodeWeather] 获取到 {} 天预报", result.size());
            return result;

        } catch (AgentDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentDataUnavailableException("高德天气查询失败: " + e.getMessage(), e);
        }
    }

    private static int parseInt(String s) {
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 15;
        }
    }

    @Override
    public String providerName() {
        return "gaode_weather";
    }
}
