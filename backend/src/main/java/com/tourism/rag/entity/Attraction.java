package com.tourism.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 景点/美食/住宿等兴趣点实体
 *
 * category 对应 Milvus metadata 中的 category 字段，
 * 支持精确过滤：attraction / food / transport / accommodation / festival
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "attraction", indexes = {
    @Index(name = "idx_city_category", columnList = "city_code, category"),
    @Index(name = "idx_city_code", columnList = "city_code")
})
public class Attraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属城市编码（关联 City.code） */
    @Column(name = "city_code", nullable = false, length = 50)
    private String cityCode;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 分类（与 Milvus metadata category 保持一致）
     * attraction / food / transport / accommodation / festival
     */
    @Column(nullable = false, length = 50)
    private String category;

    /** 详细描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 地址 */
    @Column(length = 500)
    private String address;

    /** 门票价格（元，-1 表示免费） */
    @Column(name = "ticket_price", precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    /** 开放时间描述 */
    @Column(name = "opening_hours", length = 200)
    private String openingHours;

    /** 适合季节（逗号分隔：spring,summer,autumn,winter） */
    @Column(length = 100)
    private String seasons;

    /** 适合人群标签（JSON 数组格式：["family","couple","food_lover"]） */
    @Column(columnDefinition = "TEXT")
    private String tags;

    /** 评分（0-5） */
    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    /** 是否推荐（精选景点） */
    @Builder.Default
    private Boolean recommended = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
