package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PoiInfo {

    private String id;
    private String name;
    private String category;            // attraction / museum / park / beach / temple / shopping
    private String address;
    private double lat;
    private double lng;
    private double rating;              // 0-5
    private String openingHours;
    private String ticketPrice;         // 如 "免费" 或 "65元/人"
    private int visitDurationMinutes;   // 建议游览时长（分钟）
    private boolean indoorVenue;        // true=室内，雨天备选优先
    private List<String> tags;          // 如 ["亲子", "网红打卡", "历史"]
    private String description;
    private String dataSource;          // gaode_api / mock / rag
}
