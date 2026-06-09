package com.tourism.rag.controller;

import com.tourism.rag.entity.City;
import com.tourism.rag.repository.CityRepository;
import com.tourism.rag.service.AttractionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 城市管理控制器
 *
 * API：
 * - GET  /api/cities           获取已启用的城市列表（前端下拉框数据源）
 * - GET  /api/cities/all       获取所有城市（含未启用的，用于管理界面）
 * - POST /api/cities           新增城市（管理接口）
 * - POST /api/cities/init      初始化默认青岛城市
 */
@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityRepository cityRepository;
    private final AttractionQueryService attractionQueryService;

    /**
     * 获取已启用城市列表（仅含知识库已摄入的城市）
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getEnabledCities() {
        List<Map<String, Object>> cities = cityRepository.findByEnabledTrue().stream()
                .map(city -> Map.<String, Object>of(
                        "code", city.getCode(),
                        "nameCn", city.getNameCn(),
                        "nameEn", city.getNameEn() != null ? city.getNameEn() : "",
                        "province", city.getProvince() != null ? city.getProvince() : "",
                        "description", city.getDescription() != null ? city.getDescription() : "",
                        "coverImage", city.getCoverImage() != null ? city.getCoverImage() : "",
                        "knowledgeIngested", Boolean.TRUE.equals(city.getKnowledgeIngested())
                ))
                .toList();

        return ResponseEntity.ok(cities);
    }

    /**
     * 获取所有城市列表（含未启用的，供知识库管理界面使用）
     */
    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllCities() {
        List<Map<String, Object>> cities = cityRepository.findAll().stream()
                .map(city -> Map.<String, Object>of(
                        "code", city.getCode(),
                        "nameCn", city.getNameCn(),
                        "nameEn", city.getNameEn() != null ? city.getNameEn() : "",
                        "province", city.getProvince() != null ? city.getProvince() : "",
                        "description", city.getDescription() != null ? city.getDescription() : "",
                        "enabled", Boolean.TRUE.equals(city.getEnabled()),
                        "knowledgeIngested", Boolean.TRUE.equals(city.getKnowledgeIngested())
                ))
                .toList();

        return ResponseEntity.ok(cities);
    }

    /**
     * 城市景点列表（地图数据源，含坐标）
     */
    @GetMapping("/{code}/attractions")
    public ResponseEntity<?> getCityAttractions(@PathVariable String code) {
        return ResponseEntity.ok(attractionQueryService.listByCity(code));
    }

    /**
     * 新增城市
     *
     * 请求体示例：
     * {
     *   "code": "beijing",
     *   "nameCn": "北京",
     *   "nameEn": "Beijing",
     *   "province": "北京市",
     *   "description": "中国首都，历史文化名城"
     * }
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> addCity(@RequestBody City city) {
        if (cityRepository.existsByCode(city.getCode())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "城市编码 [" + city.getCode() + "] 已存在")
            );
        }
        city.setEnabled(false);
        city.setKnowledgeIngested(false);
        City saved = cityRepository.save(city);
        return ResponseEntity.ok(Map.of(
                "code", saved.getCode(),
                "nameCn", saved.getNameCn(),
                "nameEn", saved.getNameEn() != null ? saved.getNameEn() : "",
                "province", saved.getProvince() != null ? saved.getProvince() : "",
                "description", saved.getDescription() != null ? saved.getDescription() : "",
                "enabled", false,
                "knowledgeIngested", false
        ));
    }

    /**
     * 初始化默认城市数据（系统首次部署时调用一次）
     */
    @PostMapping("/init")
    @Transactional
    public ResponseEntity<Map<String, Object>> initDefaultCities() {
        if (!cityRepository.existsByCode("qingdao")) {
            cityRepository.save(City.builder()
                    .code("qingdao")
                    .nameCn("青岛")
                    .nameEn("Qingdao")
                    .province("山东省")
                    .description("中国著名滨海旅游城市，以啤酒、海鲜、红瓦绿树著称")
                    .enabled(false)
                    .knowledgeIngested(false)
                    .build());
        }

        return ResponseEntity.ok(Map.of(
                "message", "默认城市初始化完成",
                "hint", "调用 POST /api/ingest/qingdao 开始摄入青岛知识库"
        ));
    }
}
