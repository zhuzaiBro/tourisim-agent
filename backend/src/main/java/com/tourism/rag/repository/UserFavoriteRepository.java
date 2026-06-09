package com.tourism.rag.repository;

import com.tourism.rag.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {
    List<UserFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserFavorite> findByIdAndUserId(Long id, Long userId);
}
