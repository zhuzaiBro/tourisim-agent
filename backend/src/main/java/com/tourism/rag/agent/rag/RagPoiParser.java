package com.tourism.rag.agent.rag;

import com.tourism.rag.dto.agent.PoiInfo;
import dev.langchain4j.rag.content.Content;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 RAG 检索到的 attraction 文档块解析为 {@link PoiInfo}。
 */
@Component
public class RagPoiParser {

    private static final Pattern NAME_HEADER = Pattern.compile("(?m)^###\\s+(.+)$");
    private static final Pattern NAME_BRACKET = Pattern.compile("【([^】]+)】");
    private static final Pattern TICKET = Pattern.compile("(?m)(?:- \\*\\*)?门票(?:\\*\\*)?[：:]\\s*(.+)$");
    private static final Pattern HOURS = Pattern.compile("(?m)(?:- \\*\\*)?开放时间(?:\\*\\*)?[：:]\\s*(.+)$");
    private static final Pattern ADDRESS = Pattern.compile("(?m)(?:- \\*\\*)?地址(?:\\*\\*)?[：:]\\s*(.+)$");

    public List<PoiInfo> parseFromContents(List<Content> contents, String cityCode) {
        Map<String, PoiInfo> byName = new LinkedHashMap<>();
        for (Content content : contents) {
            String text = content.textSegment().text();
            if (text == null || text.isBlank()) {
                continue;
            }
            PoiInfo poi = parseSegment(text, cityCode);
            if (poi != null && poi.getName() != null && !poi.getName().isBlank()) {
                byName.putIfAbsent(normalizeName(poi.getName()), poi);
            }
        }
        return new ArrayList<>(byName.values());
    }

    PoiInfo parseSegment(String text, String cityCode) {
        String name = extractName(text);
        if (name == null) {
            return null;
        }

        String ticket = matchGroup(TICKET, text);
        String hours = matchGroup(HOURS, text);
        String address = matchGroup(ADDRESS, text);

        boolean indoor = text.contains("博物馆") || text.contains("海洋馆") || text.contains("室内");

        return PoiInfo.builder()
                .id("rag-" + cityCode + "-" + normalizeName(name))
                .name(name.trim())
                .category("attraction")
                .address(address != null ? address.trim() : "")
                .lat(0)
                .lng(0)
                .rating(4.5)
                .openingHours(hours != null ? hours.trim() : "请提前确认")
                .ticketPrice(ticket != null ? ticket.trim() : "请查询景区")
                .visitDurationMinutes(120)
                .indoorVenue(indoor)
                .description(summarize(text))
                .dataSource("rag")
                .build();
    }

    public void enrich(PoiInfo target, PoiInfo rag) {
        if (rag == null) {
            return;
        }
        if (isPlaceholder(target.getOpeningHours()) && !isPlaceholder(rag.getOpeningHours())) {
            target.setOpeningHours(rag.getOpeningHours());
        }
        if (isPlaceholder(target.getTicketPrice()) && !isPlaceholder(rag.getTicketPrice())) {
            target.setTicketPrice(rag.getTicketPrice());
        }
        if ((target.getDescription() == null || target.getDescription().isBlank())
                && rag.getDescription() != null) {
            target.setDescription(rag.getDescription());
        }
        if ((target.getAddress() == null || target.getAddress().isBlank())
                && rag.getAddress() != null && !rag.getAddress().isBlank()) {
            target.setAddress(rag.getAddress());
        }
        if (rag.isIndoorVenue()) {
            target.setIndoorVenue(true);
        }
    }

    public String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("(景区|风景区|公园|博物馆)$", "").trim();
    }

    private String extractName(String text) {
        Matcher header = NAME_HEADER.matcher(text);
        if (header.find()) {
            return header.group(1).trim();
        }
        Matcher bracket = NAME_BRACKET.matcher(text);
        if (bracket.find()) {
            return bracket.group(1).trim();
        }
        return null;
    }

    private String matchGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private String summarize(String text) {
        String oneLine = text.lines()
                .map(String::trim)
                .filter(l -> !l.isBlank() && !l.startsWith("#") && !l.startsWith("- **"))
                .findFirst()
                .orElse(text.substring(0, Math.min(120, text.length())));
        return oneLine.length() > 150 ? oneLine.substring(0, 147) + "..." : oneLine;
    }

    private boolean isPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.contains("请提前确认") || value.contains("请咨询") || value.contains("请查询");
    }
}
