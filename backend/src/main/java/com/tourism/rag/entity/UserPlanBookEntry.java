package com.tourism.rag.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_plan_books",
        uniqueConstraints = @UniqueConstraint(name = "uk_plan_user_itinerary", columnNames = {"user_id", "itinerary_id"}),
        indexes = @Index(name = "idx_plan_user", columnList = "user_id"))
public class UserPlanBookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "itinerary_id", nullable = false, length = 36)
    private String itineraryId;

    @Column(name = "custom_title", length = 200)
    private String customTitle;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
