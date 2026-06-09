package com.tourism.rag.agent.provider;

import com.tourism.rag.dto.agent.WeatherInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock 天气提供者——基于城市 + 月份生成仿真天气数据，保证本地演示可用。
 */
@Slf4j
@Component
public class MockWeatherProvider implements WeatherProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * city -> month(1-12) -> [condition, conditionText, tempHigh, tempLow, windDir, windScale, humidity, uv]
     * condition: sunny / partly_cloudy / cloudy / light_rain / moderate_rain / thunderstorm / snow / fog
     */
    private static final Map<Integer, Object[]> QINGDAO_MONTHLY = Map.ofEntries(
            Map.entry(1,  new Object[]{"cloudy",       "阴",      6,  -2, "西北", "4", 55, "弱"}),
            Map.entry(2,  new Object[]{"cloudy",       "多云",    9,   0, "西北", "4", 58, "弱"}),
            Map.entry(3,  new Object[]{"partly_cloudy","晴间多云",14,  5, "东南", "3", 62, "中等"}),
            Map.entry(4,  new Object[]{"sunny",        "晴",      18, 10, "东南", "3", 65, "中等"}),
            Map.entry(5,  new Object[]{"sunny",        "晴",      23, 15, "东南", "3", 68, "强"}),
            Map.entry(6,  new Object[]{"partly_cloudy","晴间多云",27, 20, "东南", "3", 75, "强"}),
            Map.entry(7,  new Object[]{"light_rain",   "小雨",    29, 23, "东南", "4", 85, "中等"}),
            Map.entry(8,  new Object[]{"light_rain",   "小雨",    30, 24, "东南", "4", 82, "中等"}),
            Map.entry(9,  new Object[]{"sunny",        "晴",      25, 18, "东北", "3", 70, "中等"}),
            Map.entry(10, new Object[]{"partly_cloudy","晴间多云",19, 12, "东北", "3", 62, "弱"}),
            Map.entry(11, new Object[]{"cloudy",       "多云",    13,  6, "西北", "4", 58, "弱"}),
            Map.entry(12, new Object[]{"cloudy",       "阴",       7,  0, "西北", "5", 52, "弱"})
    );

    private static final Map<Integer, Object[]> BEIJING_MONTHLY = Map.ofEntries(
            Map.entry(1,  new Object[]{"sunny",        "晴",      2,  -8, "西北", "3", 30, "弱"}),
            Map.entry(2,  new Object[]{"sunny",        "晴",      7,  -5, "西北", "3", 32, "弱"}),
            Map.entry(3,  new Object[]{"partly_cloudy","晴间多云",14,  2, "西南", "3", 38, "中等"}),
            Map.entry(4,  new Object[]{"partly_cloudy","晴间多云",22,  9, "南",   "3", 42, "强"}),
            Map.entry(5,  new Object[]{"sunny",        "晴",      29, 15, "南",   "2", 45, "很强"}),
            Map.entry(6,  new Object[]{"light_rain",   "小雨",    33, 20, "东南", "2", 55, "强"}),
            Map.entry(7,  new Object[]{"moderate_rain","中雨",    31, 23, "东南", "3", 72, "中等"}),
            Map.entry(8,  new Object[]{"light_rain",   "小雨",    30, 22, "东南", "2", 68, "中等"}),
            Map.entry(9,  new Object[]{"sunny",        "晴",      25, 15, "东北", "2", 50, "中等"}),
            Map.entry(10, new Object[]{"sunny",        "晴",      17,  7, "西北", "3", 42, "弱"}),
            Map.entry(11, new Object[]{"cloudy",       "多云",     8, -1, "西北", "4", 38, "弱"}),
            Map.entry(12, new Object[]{"sunny",        "晴",       3, -7, "西北", "4", 30, "弱"})
    );

    private static final Map<Integer, Object[]> SHANGHAI_MONTHLY = Map.ofEntries(
            Map.entry(1,  new Object[]{"cloudy",       "阴",       8,  3, "东北", "3", 70, "弱"}),
            Map.entry(2,  new Object[]{"light_rain",   "小雨",    10,  4, "东北", "3", 72, "弱"}),
            Map.entry(3,  new Object[]{"light_rain",   "小雨",    14,  9, "东南", "3", 75, "弱"}),
            Map.entry(4,  new Object[]{"partly_cloudy","晴间多云",20, 13, "东南", "2", 72, "中等"}),
            Map.entry(5,  new Object[]{"sunny",        "晴",      26, 18, "东南", "2", 68, "强"}),
            Map.entry(6,  new Object[]{"light_rain",   "小到中雨",30, 23, "东南", "3", 78, "强"}),
            Map.entry(7,  new Object[]{"sunny",        "晴",      34, 27, "东南", "2", 75, "很强"}),
            Map.entry(8,  new Object[]{"sunny",        "晴",      33, 27, "东南", "2", 73, "很强"}),
            Map.entry(9,  new Object[]{"partly_cloudy","晴间多云",29, 22, "东北", "2", 68, "中等"}),
            Map.entry(10, new Object[]{"sunny",        "晴",      23, 16, "东北", "2", 62, "中等"}),
            Map.entry(11, new Object[]{"cloudy",       "多云",    16,  9, "东北", "3", 65, "弱"}),
            Map.entry(12, new Object[]{"cloudy",       "阴",      10,  4, "东北", "3", 68, "弱"})
    );

    private static final Map<Integer, Object[]> XIAN_MONTHLY = Map.ofEntries(
            Map.entry(1,  new Object[]{"fog",          "雾",       4, -3, "东北", "2", 65, "弱"}),
            Map.entry(2,  new Object[]{"cloudy",       "多云",     9,  1, "东北", "2", 60, "弱"}),
            Map.entry(3,  new Object[]{"partly_cloudy","晴间多云",16,  6, "南",   "2", 55, "中等"}),
            Map.entry(4,  new Object[]{"sunny",        "晴",      23, 12, "南",   "2", 50, "强"}),
            Map.entry(5,  new Object[]{"sunny",        "晴",      29, 17, "南",   "2", 48, "很强"}),
            Map.entry(6,  new Object[]{"sunny",        "晴",      34, 22, "南",   "2", 45, "很强"}),
            Map.entry(7,  new Object[]{"light_rain",   "小雨",    33, 24, "东南", "3", 62, "中等"}),
            Map.entry(8,  new Object[]{"light_rain",   "小雨",    32, 23, "东南", "3", 65, "中等"}),
            Map.entry(9,  new Object[]{"partly_cloudy","多云",    26, 17, "东南", "2", 62, "弱"}),
            Map.entry(10, new Object[]{"cloudy",       "阴",      19, 11, "东北", "2", 65, "弱"}),
            Map.entry(11, new Object[]{"fog",          "雾",      11,  4, "东北", "2", 70, "弱"}),
            Map.entry(12, new Object[]{"fog",          "雾/霾",    5, -2, "东北", "2", 68, "弱"})
    );

    private static final Map<Integer, Object[]> CHENGDU_MONTHLY = Map.ofEntries(
            Map.entry(1,  new Object[]{"cloudy",       "阴",       8,  3, "东北", "1", 80, "弱"}),
            Map.entry(2,  new Object[]{"cloudy",       "阴",      12,  6, "东北", "1", 78, "弱"}),
            Map.entry(3,  new Object[]{"partly_cloudy","晴间多云",18, 10, "南",   "1", 72, "弱"}),
            Map.entry(4,  new Object[]{"partly_cloudy","多云",    23, 14, "南",   "1", 70, "中等"}),
            Map.entry(5,  new Object[]{"light_rain",   "小雨",    27, 18, "南",   "2", 72, "中等"}),
            Map.entry(6,  new Object[]{"light_rain",   "小到中雨",29, 21, "南",   "2", 78, "中等"}),
            Map.entry(7,  new Object[]{"moderate_rain","中雨",    30, 23, "东南", "2", 82, "弱"}),
            Map.entry(8,  new Object[]{"light_rain",   "小雨",    31, 23, "东南", "2", 80, "弱"}),
            Map.entry(9,  new Object[]{"cloudy",       "多云",    25, 18, "东北", "1", 78, "弱"}),
            Map.entry(10, new Object[]{"cloudy",       "阴",      18, 13, "东北", "1", 82, "弱"}),
            Map.entry(11, new Object[]{"cloudy",       "阴",      13,  8, "东北", "1", 80, "弱"}),
            Map.entry(12, new Object[]{"cloudy",       "阴",       9,  4, "东北", "1", 78, "弱"})
    );

    /** 城市代码 -> 月度天气模板 */
    private static final Map<String, Map<Integer, Object[]>> CITY_WEATHER = Map.of(
            "qingdao", QINGDAO_MONTHLY,
            "beijing",  BEIJING_MONTHLY,
            "shanghai", SHANGHAI_MONTHLY,
            "xian",     XIAN_MONTHLY,
            "chengdu",  CHENGDU_MONTHLY
    );

    private static final String[] SUNNY_CONDITIONS = {"sunny", "partly_cloudy"};
    private static final String[] RAINY_CONDITIONS  = {"light_rain", "moderate_rain", "thunderstorm"};

    @Override
    public List<WeatherInfo> getWeather(String cityCode, String cityName, String startDate, String endDate) {
        log.info("[MockWeather] cityCode={}, {}->{}", cityCode, startDate, endDate);
        LocalDate start = LocalDate.parse(startDate, FMT);
        LocalDate end   = LocalDate.parse(endDate,   FMT);
        List<WeatherInfo> result = new ArrayList<>();
        Random rand = new Random(cityCode.hashCode());

        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            result.add(buildWeatherForDate(cityCode, cityName, cur, rand));
            cur = cur.plusDays(1);
        }
        return result;
    }

    private WeatherInfo buildWeatherForDate(String cityCode, String cityName, LocalDate date, Random rand) {
        Map<Integer, Object[]> monthly = CITY_WEATHER.getOrDefault(
                cityCode.toLowerCase(), QINGDAO_MONTHLY);
        Object[] tmpl = monthly.get(date.getMonthValue());

        // 在模板基础上加一点随机波动
        String condition     = (String) tmpl[0];
        String conditionText = (String) tmpl[1];
        int    tempHigh      = (int) tmpl[2] + rand.nextInt(3) - 1;
        int    tempLow       = (int) tmpl[3] + rand.nextInt(3) - 1;
        String windDir       = (String) tmpl[4];
        String windScale     = (String) tmpl[5];
        int    humidity      = (int) tmpl[6] + rand.nextInt(5) - 2;
        String uv            = (String) tmpl[7];

        boolean outdoorFriendly = !condition.contains("rain")
                && !condition.contains("snow")
                && !condition.contains("thunderstorm")
                && !condition.contains("fog");

        return WeatherInfo.builder()
                .date(date.format(FMT))
                .condition(condition)
                .conditionText(conditionText)
                .tempHigh(tempHigh)
                .tempLow(tempLow)
                .windDir(windDir)
                .windScale(windScale)
                .humidity(Math.min(99, Math.max(10, humidity)))
                .uvIndex(uv)
                .precipitation(condition.contains("rain") ? "有降水" : "无降水")
                .outdoorFriendly(outdoorFriendly)
                .dataSource("mock")
                .build();
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
