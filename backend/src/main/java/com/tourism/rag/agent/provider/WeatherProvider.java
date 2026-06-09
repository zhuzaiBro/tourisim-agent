package com.tourism.rag.agent.provider;

import com.tourism.rag.dto.agent.WeatherInfo;

import java.util.List;

/**
 * 天气数据提供者接口。
 * 实现：GaodeWeatherProvider（高德天气）、MockWeatherProvider（兜底）。
 */
public interface WeatherProvider {

    /**
     * 获取指定城市在日期范围内的逐日天气。
     *
     * @param cityCode  城市代码，如 "qingdao"
     * @param cityName  城市中文名，如 "青岛"
     * @param startDate yyyy-MM-dd
     * @param endDate   yyyy-MM-dd
     * @return 按日期排列的天气列表
     */
    List<WeatherInfo> getWeather(String cityCode, String cityName, String startDate, String endDate);

    /** 标识当前 Provider 名称，用于日志 */
    String providerName();
}
