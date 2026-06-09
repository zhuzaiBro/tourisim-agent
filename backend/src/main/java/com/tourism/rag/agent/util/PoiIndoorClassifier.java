package com.tourism.rag.agent.util;

import com.tourism.rag.dto.agent.PoiInfo;

import java.util.List;

/**
 * 根据名称/类别推断室内景点，用于雨天备选路线。
 */
public final class PoiIndoorClassifier {

    private static final List<String> INDOOR_HINTS = List.of(
            "博物馆", "纪念馆", "美术馆", "艺术馆", "科技馆", "展览馆", "展览中心",
            "海洋馆", "水族馆", "天文馆", "图书馆", "文化中心", "艺术馆",
            "商场", "购物中心", "百货", "万达", "银泰", "太古里",
            "寺庙", "道观", "教堂", "清真寺",
            "室内", "游乐", "主题乐园", "冰雪世界", "温泉", "汗蒸"
    );

    private PoiIndoorClassifier() {}

    public static boolean isIndoor(PoiInfo poi) {
        if (poi == null) return false;
        if (poi.isIndoorVenue()) return true;
        String text = ((poi.getName() != null ? poi.getName() : "")
                + " " + (poi.getCategory() != null ? poi.getCategory() : "")).toLowerCase();
        return INDOOR_HINTS.stream().anyMatch(text::contains);
    }

    public static void applyIndoorFlags(List<PoiInfo> pois) {
        if (pois == null) return;
        for (PoiInfo p : pois) {
            if (isIndoor(p)) {
                p.setIndoorVenue(true);
            }
        }
    }

    public static List<PoiInfo> filterIndoor(List<PoiInfo> pois) {
        if (pois == null) return List.of();
        return pois.stream().filter(PoiIndoorClassifier::isIndoor).toList();
    }
}
