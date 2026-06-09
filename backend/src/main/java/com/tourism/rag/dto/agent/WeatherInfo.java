package com.tourism.rag.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeatherInfo {

    private String date;               // yyyy-MM-dd
    private String condition;          // sunny / cloudy / rainy / snowy / foggy / thunderstorm
    private String conditionText;      // 晴 / 多云 / 小雨 / 大雨 / 雪
    private int tempHigh;              // 最高气温（℃）
    private int tempLow;               // 最低气温（℃）
    private String windDir;            // 风向，如 东北
    private String windScale;          // 风力等级，如 3
    private int humidity;              // 湿度百分比
    private String uvIndex;            // 紫外线等级：弱/中等/强/很强
    private String precipitation;      // 降水量描述
    private boolean outdoorFriendly;   // 是否适合户外活动
    private String dataSource;         // hefeng_api / mock
}
