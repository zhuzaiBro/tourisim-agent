package com.tourism.rag.repository;

import com.tourism.rag.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findByConversationIdOrderByTimestampAsc(String conversationId);

    @Transactional
    void deleteByConversationId(String conversationId);

    long countByTimestampAfter(LocalDateTime after);

    @Query("SELECT FUNCTION('DATE', m.timestamp) as dt, COUNT(m) FROM ConversationMessage m WHERE m.timestamp >= :since GROUP BY FUNCTION('DATE', m.timestamp) ORDER BY dt")
    List<Object[]> countByDaySince(@Param("since") LocalDateTime since);
}
