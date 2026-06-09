package com.tourism.rag.agent.guide;

import com.tourism.rag.dto.agent.XiaohongshuNote;

import java.util.List;

/** 将一批小红书笔记整理为 LLM 可读的语料 */
public final class XhsNoteCorpus {

    private XhsNoteCorpus() {}

    public static String build(List<XiaohongshuNote> notes, int limit) {
        if (notes == null || notes.isEmpty()) return "";
        StringBuilder corpus = new StringBuilder();
        List<XiaohongshuNote> top = notes.stream().limit(limit).toList();
        for (int i = 0; i < top.size(); i++) {
            XiaohongshuNote n = top.get(i);
            corpus.append(i + 1).append(". 《").append(n.getTitle()).append("》");
            if (n.getAuthor() != null && !n.getAuthor().isBlank()) {
                corpus.append(" @").append(n.getAuthor());
            }
            corpus.append(" | 赞:").append(n.getLikes()).append(" 藏:").append(n.getCollects());
            if (n.getDescription() != null && !n.getDescription().isBlank()) {
                corpus.append("\n   ").append(truncate(n.getDescription(), 280));
            }
            corpus.append("\n");
        }
        return corpus.toString();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}
