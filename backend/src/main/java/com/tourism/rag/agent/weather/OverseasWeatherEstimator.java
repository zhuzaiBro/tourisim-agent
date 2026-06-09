package com.tourism.rag.agent.weather;

import com.tourism.rag.dto.agent.WeatherInfo;
import com.tourism.rag.util.CityNameResolver;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 境外/自定义目的地在高德天气不可用时的季节气候估算。
 */
public final class OverseasWeatherEstimator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, int[]> CLIMATE = Map.ofEntries(
            Map.entry("新加坡", new int[]{31, 24}),
            Map.entry("singapore", new int[]{31, 24}),
            Map.entry("东京", new int[]{28, 18}),
            Map.entry("tokyo", new int[]{28, 18}),
            Map.entry("曼谷", new int[]{33, 25}),
            Map.entry("bangkok", new int[]{33, 25}),
            Map.entry("首尔", new int[]{27, 15}),
            Map.entry("seoul", new int[]{27, 15}),
            Map.entry("巴黎", new int[]{24, 12}),
            Map.entry("paris", new int[]{24, 12}),
            Map.entry("伦敦", new int[]{22, 12}),
            Map.entry("london", new int[]{22, 12}),
            Map.entry("吉隆坡", new int[]{32, 23}),
            Map.entry("kualalumpur", new int[]{32, 23}),
            Map.entry("kuala lumpur", new int[]{32, 23})
    );

    private OverseasWeatherEstimator() {}

    public static boolean supports(String cityCode, String cityName) {
        return CityNameResolver.isCustomDestination(cityCode) || resolveClimate(cityName, cityCode) != null;
    }

    public static List<WeatherInfo> estimate(String cityCode, String cityName,
                                              String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate, FMT);
        LocalDate end = LocalDate.parse(endDate, FMT);
        int[] base = resolveClimate(cityName, cityCode);
        if (base == null) {
            base = new int[]{28, 18};
        }

        List<WeatherInfo> list = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            int month = d.getMonthValue();
            int high = adjustHigh(base[0], month);
            int low = adjustLow(base[1], month);
            boolean outdoor = month >= 4 && month <= 10;
            list.add(WeatherInfo.builder()
                    .date(d.format(FMT))
                    .condition(outdoor ? "partly_cloudy" : "cloudy")
                    .conditionText(outdoor ? "多云" : "阴")
                    .tempHigh(high)
                    .tempLow(low)
                    .windScale("2级")
                    .outdoorFriendly(outdoor)
                    .dataSource("climate_estimate")
                    .build());
        }
        return list;
    }

    private static int[] resolveClimate(String cityName, String cityCode) {
        if (cityName != null) {
            int[] hit = CLIMATE.get(cityName.trim());
            if (hit != null) return hit;
        }
        if (cityCode != null && cityCode.startsWith(CityNameResolver.CUSTOM_PREFIX)) {
            String name = cityCode.substring(CityNameResolver.CUSTOM_PREFIX.length()).trim();
            return CLIMATE.get(name);
        }
        if (cityCode != null) {
            return CLIMATE.get(cityCode.toLowerCase().trim());
        }
        return null;
    }

    private static int adjustHigh(int base, int month) {
        if (month >= 6 && month <= 8) return base + 2;
        if (month >= 12 || month <= 2) return base - 3;
        return base;
    }

    private static int adjustLow(int base, int month) {
        if (month >= 6 && month <= 8) return base + 1;
        if (month >= 12 || month <= 2) return base - 4;
        return base;
    }
}
