package com.tourism.rag.repository;

import com.tourism.rag.entity.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserNoteRepository extends JpaRepository<UserNote, String> {
    List<UserNote> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<UserNote> findByIdAndUserId(String id, Long userId);
}
