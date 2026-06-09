package com.tourism.rag.agent.poi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.util.CityNameResolver;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当高德/RAG 景点不足时，用 LLM 补充海外或自定义目的地的经典景点（非 Mock 静态表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmPoiEnrichmentService {

    private static final int MIN_TARGET = 8;

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public List<PoiInfo> enrichIfNeeded(String cityCode, String cityName,
                                         List<PoiInfo> existing, double centerLat, double centerLng) {
        if (existing != null && existing.size() >= MIN_TARGET) {
            return existing;
        }
        int need = MIN_TARGET - (existing != null ? existing.size() : 0);
        if (need <= 0) return existing;

        try {
            List<PoiInfo> generated = generatePois(cityName, need, centerLat, centerLng);
            List<PoiInfo> merged = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            if (existing != null) {
                for (PoiInfo p : existing) {
                    String key = normalize(p.getName());
                    if (seen.add(key)) merged.add(p);
                }
            }
            for (PoiInfo p : generated) {
                String key = normalize(p.getName());
                if (seen.add(key)) merged.add(p);
            }
            log.info("[LlmPoi] {} 补充 {} 个景点，合计 {}", cityName, generated.size(), merged.size());
            return merged;
        } catch (Exception e) {
            log.warn("[LlmPoi] LLM 景点补充失败 {}: {}", cityName, e.getMessage());
            return existing != null ? existing : List.of();
        }
    }

    public static boolean needsEnrichment(String cityCode, String cityName) {
        if (cityCode != null && cityCode.startsWith(CityNameResolver.CUSTOM_PREFIX)) {
            return true;
        }
        if (cityName != null) {
            String n = cityName.trim();
            return n.contains("新加坡") || n.contains("日本") || n.contains("泰国")
                    || n.contains("韩国") || n.contains("欧洲") || n.contains("美国")
                    || n.contains("马来西亚") || n.contains("越南") || n.contains("印尼");
        }
        return false;
    }

    private List<PoiInfo> generatePois(String cityName, int count, double lat, double lng) throws Exception {
        String prompt = """
                你是专业旅游规划师。请为目的地「%s」列出 %d 个必游景点/体验。
                只输出 JSON 数组，不要 markdown，格式：
                [{"name":"景点名","category":"类型","address":"地址简述","rating":4.5,"visitMinutes":120,"indoor":false}]
                要求：真实知名景点、含地标+文化+休闲、适合一日游或多日行程拆分。
                """.formatted(cityName, count);

        String raw = chatLanguageModel.generate(prompt).trim();
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("LLM 未返回 JSON 数组");
        }
        JsonNode arr = objectMapper.readTree(raw.substring(start, end + 1));
        List<PoiInfo> result = new ArrayList<>();
        int i = 0;
        for (JsonNode node : arr) {
            double offsetLat = lat + (i % 5) * 0.008 * Math.cos(i * 0.9);
            double offsetLng = lng + (i % 5) * 0.008 * Math.sin(i * 0.9);
            result.add(PoiInfo.builder()
                    .id("llm-" + cityName + "-" + i)
                    .name(node.path("name").asText("景点" + i))
                    .category(node.path("category").asText("景点"))
                    .address(node.path("address").asText(cityName))
                    .lat(offsetLat)
                    .lng(offsetLng)
                    .rating(node.path("rating").asDouble(4.5))
                    .openingHours("请提前确认开放时间")
                    .ticketPrice("请查询官网")
                    .visitDurationMinutes(Math.max(60, node.path("visitMinutes").asInt(120)))
                    .indoorVenue(node.path("indoor").asBoolean(false))
                    .description(node.path("name").asText() + " — " + cityName + "经典体验")
                    .dataSource("llm_knowledge")
                    .build());
            i++;
        }
        return result;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
