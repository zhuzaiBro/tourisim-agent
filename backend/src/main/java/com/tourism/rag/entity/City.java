package com.tourism.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 城市实体
 *
 * 扩展新城市只需在此表插入一条记录，
 * 并准备对应的知识库文档（Markdown/PDF）即可。
 *
 * 多城市扩展示例：
 *   INSERT INTO city (code, name_cn, name_en, province, enabled) VALUES
 *   ('beijing', '北京', 'Beijing', '北京市', 1),
 *   ('shanghai', '上海', 'Shanghai', '上海市', 1);
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "city")
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 城市编码，全小写英文，用于 Milvus metadata 过滤 */
    @Column(unique = true, nullable = false, length = 50)
    private String code;  // e.g., "qingdao", "beijing", "shanghai"

    /** 中文名 */
    @Column(name = "name_cn", nullable = false, length = 50)
    private String nameCn;

    /** 英文名 */
    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    /** 省份/直辖市 */
    @Column(length = 50)
    private String province;

    /** 城市简介 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 封面图 URL */
    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /** 是否启用（false 表示知识库尚未摄入，前端不展示） */
    @Builder.Default
    private Boolean enabled = false;

    /** 知识库是否已摄入 */
    @Column(name = "knowledge_ingested")
    @Builder.Default
    private Boolean knowledgeIngested = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
