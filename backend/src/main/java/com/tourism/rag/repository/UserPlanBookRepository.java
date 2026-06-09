package com.tourism.rag.repository;

import com.tourism.rag.entity.UserPlanBookEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPlanBookRepository extends JpaRepository<UserPlanBookEntry, Long> {
    List<UserPlanBookEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<UserPlanBookEntry> findByUserIdAndItineraryId(Long userId, String itineraryId);
    void deleteByUserIdAndItineraryId(Long userId, String itineraryId);
}
