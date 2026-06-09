package com.tourism.rag.agent.guide;

import com.tourism.rag.dto.agent.XiaohongshuNote;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 按用户游玩天数对小红书笔记打分排序。
 */
@Component
public class XhsNoteRanker {

    private static final Pattern DAY_PATTERN = Pattern.compile(
            "(\\d+)\\s*(天|日|晚)|(\\d+)\\s*day", Pattern.CASE_INSENSITIVE);

    public List<XiaohongshuNote> rankAndFilter(List<XiaohongshuNote> notes, int totalDays, int limit) {
        if (notes == null || notes.isEmpty()) return List.of();
        int days = Math.max(1, totalDays);

        return notes.stream()
                .map(n -> new Scored(n, score(n, days)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(limit, 1))
                .map(Scored::note)
                .collect(Collectors.toList());
    }

    double score(XiaohongshuNote note, int userDays) {
        String text = (note.getTitle() + " " + nullToEmpty(note.getDescription())).toLowerCase();
        int noteDays = extractDays(text);

        double engagement = Math.log10(Math.max(1, note.getLikes() + note.getCollects() + 1)) * 10;
        double dayScore = dayMatchScore(noteDays, userDays);

        // 一日游/周末游关键词
        if (userDays == 1 && (text.contains("一日游") || text.contains("1日") || text.contains("当天"))) {
            dayScore += 15;
        }
        if (userDays >= 3 && text.contains(userDays + "天")) {
            dayScore += 25;
        }

        return engagement + dayScore;
    }

    public static int extractDays(String text) {
        int max = 0;
        Matcher m = DAY_PATTERN.matcher(text);
        while (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(3);
            if (g != null) {
                try {
                    max = Math.max(max, Integer.parseInt(g));
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    private static double dayMatchScore(int noteDays, int userDays) {
        if (noteDays <= 0) return 5; // 未标明天数，保留中等权重
        if (noteDays == userDays) return 40;
        if (Math.abs(noteDays - userDays) == 1) return 20;
        if (noteDays < userDays) return 10; // 短攻略可参考
        if (noteDays <= userDays + 2) return 5;
        return -20; // 天数差太多，降权
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record Scored(XiaohongshuNote note, double score) {}
}
