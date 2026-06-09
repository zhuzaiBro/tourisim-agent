package com.tourism.rag.repository;

import com.tourism.rag.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByCode(String code);

    List<City> findByEnabledTrue();

    boolean existsByCode(String code);
}
