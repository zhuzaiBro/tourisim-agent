package com.tourism.rag.repository;

import com.tourism.rag.entity.ItineraryRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRecordRepository extends JpaRepository<ItineraryRecord, String> {

    List<ItineraryRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ItineraryRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<ItineraryRecord> findByCityCodeOrderByCreatedAtDesc(String cityCode);
}
