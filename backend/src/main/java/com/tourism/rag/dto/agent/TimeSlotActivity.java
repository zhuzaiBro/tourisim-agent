package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeSlotActivity {

    private String timeSlot;            // 如 "09:00-11:30"
    private String activity;            // 活动名称
    private String type;                // attraction / food / transport / rest / shopping
    private PoiInfo poi;                // 关联景点（可为 null，如交通/休息）
    private int durationMinutes;
    private String transportFromPrev;   // 从上一个地点的交通方式
    private int transportMinutes;       // 交通耗时
    private double estimatedCost;       // 预估费用（元）
    private String notes;               // 注意事项
}
