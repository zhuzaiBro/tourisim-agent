package com.tourism.rag.agent.provider.xhs;

import com.tourism.rag.agent.guide.GuidePoiExtractor;
import com.tourism.rag.agent.guide.XhsBatchOpinionFilter;
import com.tourism.rag.agent.guide.XhsNoteRanker;
import com.tourism.rag.agent.poi.XhsPoiGaodeEnricher;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import com.tourism.rag.util.CityGeoResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通过小红书笔记搜索为行程规划提供攻略型 POI。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaohongshuGuideProvider {

    private final XiaohongshuApiClient apiClient;
    private final GuidePoiExtractor guidePoiExtractor;
    private final XhsNoteRanker noteRanker;
    private final XhsPoiGaodeEnricher gaodeEnricher;
    private final XhsBatchOpinionFilter opinionFilter;
    private final CityGeoResolver cityGeoResolver;

    public boolean isConfigured() {
        return apiClient.isConfigured();
    }

    public List<PoiInfo> searchPois(String cityCode, String cityName,
                                     List<String> keywords, int maxResults, int totalDays) {
        if (!isConfigured()) {
            return List.of();
        }

        int days = Math.max(1, totalDays);
        double[] center = cityGeoResolver.resolve(cityCode, cityName);
        List<XiaohongshuNote> allNotes = collectNotes(cityName, keywords, days);

        if (allNotes.isEmpty()) {
            log.info("[XHS] 未搜到攻略笔记 city={}", cityName);
            return List.of();
        }

        List<XiaohongshuNote> ranked = noteRanker.rankAndFilter(allNotes, days, Math.min(15, days * 5));
        log.info("[XHS] city={} 原始 {} 篇 → 按 {} 天筛选排序后 {} 篇",
                cityName, allNotes.size(), days, ranked.size());
        logRankedNotes(ranked, days);

        int extractCount = Math.min(maxResults + 4, days * 5);
        List<PoiInfo> rawPois = guidePoiExtractor.extractFromNotes(
                cityName, ranked, center[0], center[1], extractCount, days);

        List<PoiInfo> pois = opinionFilter.filterPois(cityName, ranked, rawPois, maxResults);

        return gaodeEnricher.enrichBatch(cityCode, cityName, pois);
    }

    private List<XiaohongshuNote> collectNotes(String cityName, List<String> keywords, int totalDays) {
        List<XiaohongshuNote> allNotes = new ArrayList<>();
        Set<String> seenTitles = new LinkedHashSet<>();

        for (String q : buildQueries(cityName, keywords, totalDays)) {
            List<XiaohongshuNote> batch = apiClient.searchNotes(q, 1, 20);
            logNotesForKeyword(q, batch);
            for (XiaohongshuNote n : batch) {
                if (seenTitles.add(n.getTitle())) {
                    allNotes.add(n);
                }
            }
            if (allNotes.size() >= 25) break;
        }
        return allNotes;
    }

    private void logRankedNotes(List<XiaohongshuNote> notes, int userDays) {
        log.info("[XHS] ---------- 按 {} 天筛选排序 ----------", userDays);
        for (int i = 0; i < notes.size(); i++) {
            XiaohongshuNote n = notes.get(i);
            int noteDays = XhsNoteRanker.extractDays(
                    n.getTitle() + " " + (n.getDescription() != null ? n.getDescription() : ""));
            log.info("[XHS] [{}] 笔记{}天 分{} | {}",
                    i + 1, noteDays > 0 ? noteDays : "?", userDays, formatNote(n));
        }
        log.info("[XHS] ------------------------------");
    }

    private void logNotesForKeyword(String keyword, List<XiaohongshuNote> notes) {
        if (notes.isEmpty()) {
            log.info("[XHS] keyword=\"{}\" 返回 0 篇", keyword);
            return;
        }
        log.info("[XHS] keyword=\"{}\" 返回 {} 篇", keyword, notes.size());
        for (int i = 0; i < notes.size(); i++) {
            log.info("[XHS]   {}. {}", i + 1, formatNote(notes.get(i)));
        }
    }

    private static String formatNote(XiaohongshuNote n) {
        StringBuilder sb = new StringBuilder();
        sb.append("《").append(n.getTitle()).append("》");
        if (n.getAuthor() != null && !n.getAuthor().isBlank()) {
            sb.append(" @").append(n.getAuthor());
        }
        sb.append(" | 赞:").append(n.getLikes()).append(" 藏:").append(n.getCollects());
        if (n.getDescription() != null && !n.getDescription().isBlank()) {
            sb.append(" | ").append(truncate(n.getDescription(), 120));
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private List<String> buildQueries(String cityName, List<String> keywords, int totalDays) {
        List<String> queries = new ArrayList<>();
        queries.add(cityName + " " + totalDays + "天攻略");
        queries.add(cityName + " " + totalDays + "日游");
        queries.add(cityName + " 旅游攻略");
        queries.add(cityName + " 必去景点");
        if (keywords != null) {
            for (String kw : keywords) {
                if (kw != null && !kw.isBlank()) {
                    queries.add(cityName + " " + kw);
                }
            }
        }
        return queries;
    }
}
