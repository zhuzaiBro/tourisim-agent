package com.tourism.rag.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 地图/列表展示用景点 DTO（含坐标）。
 */
@Data
@Builder
public class AttractionDto {
    private Long id;
    private String cityCode;
    private String name;
    private String category;
    private String categoryLabel;
    private String description;
    private String address;
    private String ticketPrice;
    private String openingHours;
    private Double rating;
    private Double lat;
    private Double lng;
    private String dataSource;
}
