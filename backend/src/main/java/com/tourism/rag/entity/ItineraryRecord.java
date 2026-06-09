package com.tourism.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 行程记录持久化实体。
 * 保存每次生成的行程，支持 GET /api/agent/itinerary/{id} 查询。
 */
@Entity
@Table(name = "itinerary_records",
        indexes = @Index(name = "idx_city_date", columnList = "city_code, start_date"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryRecord {

    @Id
    @Column(length = 36)
    private String id;                  // UUID

    @Column(name = "city_code", length = 50, nullable = false)
    private String cityCode;

    @Column(name = "city_name", length = 100)
    private String cityName;

    @Column(name = "start_date", length = 10)
    private String startDate;

    @Column(name = "end_date", length = 10)
    private String endDate;

    @Column(name = "total_days")
    private int totalDays;

    /** 完整的 ItineraryResponse JSON */
    @Lob
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    /** 请求参数 JSON（preferences, budget 等）*/
    @Column(name = "request_json", length = 1000)
    private String requestJson;

    @Column(name = "user_id")
    private Long userId;                // 可为 null（匿名请求）

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
