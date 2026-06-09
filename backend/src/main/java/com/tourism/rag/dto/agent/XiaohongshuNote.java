package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

/**
 * 小红书攻略笔记摘要（用于景点抽取）。
 */
@Data
@Builder
public class XiaohongshuNote {
    private String noteId;
    private String title;
    private String description;
    private String author;
    private int likes;
    private int collects;
    private String url;
}
