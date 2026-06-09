package com.tourism.rag.repository;

import com.tourism.rag.entity.Attraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    List<Attraction> findByCityCode(String cityCode);

    List<Attraction> findByCityCodeAndCategory(String cityCode, String category);

    List<Attraction> findByCityCodeAndRecommendedTrue(String cityCode);
}
