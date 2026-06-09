package com.tourism.rag.dto;

import lombok.Data;

/**
 * 数据摄入请求 DTO
 */
@Data
public class IngestRequest {

    /** 目标城市编码 */
    private String cityCode;

    /** 摄入来源类型 */
    private SourceType sourceType = SourceType.BUILTIN;

    /** 文件路径（sourceType=FILE 时有效） */
    private String filePath;

    /** 网页 URL（sourceType=URL 时有效） */
    private String url;

    /** 是否清除旧数据再写入（危险操作，需谨慎） */
    private boolean clearExisting = false;

    public enum SourceType {
        BUILTIN,  // 使用内置示例数据（硬编码）
        FILE,     // 从本地文件加载
        URL,      // 从网页抓取
        MYSQL     // 从 MySQL attraction 表加载
    }
}
