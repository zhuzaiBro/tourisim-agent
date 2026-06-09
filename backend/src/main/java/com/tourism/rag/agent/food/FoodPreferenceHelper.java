package com.tourism.rag.agent.food;

import com.tourism.rag.dto.agent.FoodRecommendation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 忌口与口味偏好的检索约束、提示词与结果过滤 */
public final class FoodPreferenceHelper {

    private FoodPreferenceHelper() {}

    public static String toPromptConstraint(List<String> dietary, List<String> taste) {
        StringBuilder sb = new StringBuilder();
        if (dietary != null && !dietary.isEmpty()) {
            sb.append("忌口（必须严格遵守，不得推荐冲突餐厅/菜品）：")
                    .append(String.join("、", dietary));
        }
        if (taste != null && !taste.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("口味偏好（优先推荐符合以下口味的餐厅）：")
                    .append(String.join("、", taste));
        }
        return sb.isEmpty() ? "无特殊忌口或口味要求" : sb.toString();
    }

    public static List<String> buildXhsQueries(String cityName,
                                                List<String> taste,
                                                List<String> dietary) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(cityName + " 美食攻略");
        queries.add(cityName + " 必吃餐厅");
        if (taste != null) {
            for (String t : taste) {
                if (t != null && !t.isBlank()) {
                    queries.add(cityName + " " + t + " 美食");
                }
            }
        }
        if (dietary != null) {
            for (String d : dietary) {
                if (d != null && !d.isBlank()) {
                    queries.add(cityName + " " + d + " 餐厅");
                }
            }
        }
        queries.add(cityName + " 特色小吃");
        return new ArrayList<>(queries);
    }

    public static String cacheKeySuffix(List<String> dietary, List<String> taste) {
        return "|d:" + join(dietary) + "|t:" + join(taste);
    }

    /** 规则过滤明显与忌口冲突的推荐（LLM 之后的兜底） */
    public static List<FoodRecommendation> applyDietaryFilter(List<FoodRecommendation> foods,
                                                               List<String> dietary) {
        if (foods == null || foods.isEmpty() || dietary == null || dietary.isEmpty()) {
            return foods != null ? foods : List.of();
        }
        Set<String> rules = dietary.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());

        return foods.stream()
                .filter(f -> !violatesDietary(f, rules))
                .collect(Collectors.toList());
    }

    private static boolean violatesDietary(FoodRecommendation food, Set<String> dietary) {
        String text = (food.getName() + " " + food.getCategory() + " "
                + nullToEmpty(food.getRecommendReason())).toLowerCase();

        if (dietary.contains("不吃辣") || dietary.contains("忌辣")) {
            if (text.contains("辣") || text.contains("麻辣") || text.contains("火锅")) return true;
        }
        if (dietary.contains("清真")) {
            if (text.contains("猪") || text.contains("培根") || text.contains("酒吧")) return true;
        }
        if (dietary.contains("素食") || dietary.contains("吃素")) {
            if (text.contains("牛") || text.contains("羊") || text.contains("猪")
                    || text.contains("海鲜") || text.contains("肉")) return true;
        }
        if (dietary.contains("不吃猪肉")) {
            if (text.contains("猪") || text.contains("培根") || text.contains("叉烧")) return true;
        }
        if (dietary.contains("海鲜过敏")) {
            if (text.contains("海鲜") || text.contains("虾") || text.contains("蟹")
                    || text.contains("鱼") || text.contains("生蚝")) return true;
        }
        if (dietary.contains("乳糖不耐")) {
            if (text.contains("奶") || text.contains("芝士") || text.contains("奶酪")
                    || text.contains("冰淇淋")) return true;
        }
        return false;
    }

    private static String join(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.stream().sorted().collect(Collectors.joining(","));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
