package com.tourism.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天响应 DTO（非流式）
 */
@Data
@Builder
public class ChatResponse {

    /** 会话 ID */
    private String sessionId;

    /** AI 回答内容（Markdown 格式） */
    private String answer;

    /** 来源引用列表（防幻觉 grounding） */
    private List<SourceReference> sources;

    /** 响应时间戳 */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** 检索到的文档块数量 */
    private Integer retrievedChunks;

    /** 使用的城市过滤器 */
    private List<String> filteredCities;

    /**
     * 来源引用
     * 每个检索到的文档块对应一个引用
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceReference {
        private String source;    // 文档来源（文件名/URL/数据库）
        private String city;      // 所属城市
        private String category;  // 分类（attraction/food/transport 等）
        private String excerpt;   // 原文摘要（前100字）
        private Double score;     // 相似度评分
    }
}
