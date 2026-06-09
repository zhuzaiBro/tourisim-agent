package com.tourism.rag.agent.guide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 从小红书攻略笔记中抽取结构化景点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuidePoiExtractor {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    public List<PoiInfo> extractFromNotes(String cityName, List<XiaohongshuNote> notes,
                                           double centerLat, double centerLng,
                                           int maxPois, int totalDays) {
        if (notes == null || notes.isEmpty()) return List.of();

        List<XiaohongshuNote> top = notes.stream().limit(12).toList();

        StringBuilder corpus = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            XiaohongshuNote n = top.get(i);
            corpus.append(i + 1).append(". 《").append(n.getTitle()).append("》");
            if (n.getDescription() != null && !n.getDescription().isBlank()) {
                corpus.append(" — ").append(truncate(n.getDescription(), 200));
            }
            corpus.append("\n");
        }

        try {
            return parseWithLlm(cityName, corpus.toString(), centerLat, centerLng, maxPois, totalDays);
        } catch (Exception e) {
            log.warn("[GuidePoi] LLM 抽取失败 {}: {}", cityName, e.getMessage());
            return List.of();
        }
    }

    private List<PoiInfo> parseWithLlm(String cityName, String corpus,
                                        double lat, double lng, int maxPois, int totalDays) throws Exception {
        int days = Math.max(1, totalDays);
        String prompt = """
                你是旅游攻略分析师。用户计划「%s」%d 天行程。根据以下小红书笔记，提取必去景点。
                只输出 JSON 数组，不要 markdown：
                [{"name":"景点名","category":"类型","tips":"游玩建议","visitMinutes":120,"indoor":false,"suggestedDay":1}]
                要求：去重、真实地名、至少 %d 个；suggestedDay 为 1-%d，按合理游览顺序分配到各天。
                
                笔记内容：
                %s
                """.formatted(cityName, days, Math.min(maxPois, days * 4), days, corpus);

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
            if (i >= maxPois) break;
            double offsetLat = lat + (i % 6) * 0.007 * Math.cos(i * 1.1);
            double offsetLng = lng + (i % 6) * 0.007 * Math.sin(i * 1.1);
            int suggestedDay = node.path("suggestedDay").asInt((i % days) + 1);
            suggestedDay = Math.max(1, Math.min(days, suggestedDay));
            List<String> tags = List.of("day:" + suggestedDay);

            result.add(PoiInfo.builder()
                    .id("xhs-" + cityName + "-" + i)
                    .name(node.path("name").asText("景点" + i))
                    .category(node.path("category").asText("景点"))
                    .address(cityName)
                    .lat(offsetLat)
                    .lng(offsetLng)
                    .rating(4.6)
                    .openingHours("请参考小红书攻略或官网")
                    .ticketPrice("请查询官网")
                    .visitDurationMinutes(Math.max(60, node.path("visitMinutes").asInt(120)))
                    .indoorVenue(node.path("indoor").asBoolean(false))
                    .tags(tags)
                    .description(node.path("tips").asText(node.path("name").asText()))
                    .dataSource("xhs_guide")
                    .build());
            i++;
        }
        log.info("[GuidePoi] 从 {} 篇笔记抽取 {} 个景点", corpus.lines().count(), result.size());
        return result;
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}
