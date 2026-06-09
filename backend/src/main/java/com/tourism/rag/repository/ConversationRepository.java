package com.tourism.rag.repository;

import com.tourism.rag.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Conversation> findByIdAndUserId(String id, Long userId);
    long countByCreatedAtAfter(LocalDateTime after);
    @Transactional
    void deleteByIdAndUserId(String id, Long userId);
}
