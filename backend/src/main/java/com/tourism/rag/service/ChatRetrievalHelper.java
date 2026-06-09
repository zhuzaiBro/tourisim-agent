package com.tourism.rag.service;

/**
 * 聊天 RAG 检索路由辅助（分类 / 跨城对比判断）。
 */
public final class ChatRetrievalHelper {

    private ChatRetrievalHelper() {}

    public static boolean isCrossCityComparison(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return question.contains("哪个")
                || question.contains("对比")
                || question.contains("比较")
                || question.contains("更适合")
                || question.contains("更好")
                || question.contains("还是")
                || question.toLowerCase().contains(" vs ");
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category.trim().toLowerCase();
    }
}
