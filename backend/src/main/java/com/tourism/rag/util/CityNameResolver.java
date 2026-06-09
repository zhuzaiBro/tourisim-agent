package com.tourism.rag.util;

import com.tourism.rag.repository.CityRepository;

import java.util.Map;

/**
 * 将 cityCode / 用户输入解析为高德与 LLM 使用的中文城市名。
 */
public final class CityNameResolver {

    public static final String CUSTOM_PREFIX = "custom:";

    private static final Map<String, String> FALLBACK_NAMES = Map.ofEntries(
            Map.entry("qingdao", "青岛"),
            Map.entry("beijing", "北京"),
            Map.entry("shanghai", "上海"),
            Map.entry("xian", "西安"),
            Map.entry("chengdu", "成都"),
            Map.entry("guilin", "桂林"),
            Map.entry("hangzhou", "杭州"),
            Map.entry("suzhou", "苏州"),
            Map.entry("xiamen", "厦门")
    );

    private CityNameResolver() {}

    public static String resolve(String cityCode, String cityNameOverride, CityRepository cityRepository) {
        if (cityNameOverride != null && !cityNameOverride.isBlank()) {
            return cityNameOverride.trim();
        }
        if (cityCode != null && cityCode.startsWith(CUSTOM_PREFIX)) {
            String name = cityCode.substring(CUSTOM_PREFIX.length()).trim();
            if (!name.isEmpty()) return name;
        }
        if (cityCode == null || cityCode.isBlank()) {
            return "";
        }
        String code = cityCode.toLowerCase().trim();
        return cityRepository.findByCode(code)
                .map(c -> c.getNameCn())
                .orElse(FALLBACK_NAMES.getOrDefault(code, code));
    }

    public static boolean isCustomDestination(String cityCode) {
        return cityCode != null && cityCode.startsWith(CUSTOM_PREFIX);
    }
}
