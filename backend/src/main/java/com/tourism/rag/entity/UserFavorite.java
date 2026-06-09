package com.tourism.rag.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_favorites", indexes = @Index(name = "idx_fav_user", columnList = "user_id"))
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "cities_json", length = 500)
    private String citiesJson;

    @Column(length = 500)
    private String question;

    @Column(name = "session_title", length = 200)
    private String sessionTitle;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
