package com.tourism.rag.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ItineraryRequest {

    @NotBlank(message = "cityCode 不能为空")
    private String cityCode;

    /** 用户自定义目的地中文名（可选，优先于 cityCode 解析） */
    private String cityName;

    @NotBlank(message = "startDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String startDate;

    @NotBlank(message = "endDate 不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String endDate;

    /**
     * 出行偏好：family / couple / food / photography / budget / culture / adventure
     */
    @Size(max = 5, message = "偏好最多选 5 项")
    private List<String> preferences;

    /** 忌口：不吃辣、清真、素食、不吃猪肉、海鲜过敏、乳糖不耐 等 */
    @Size(max = 6, message = "忌口最多选 6 项")
    private List<String> dietaryRestrictions;

    /** 口味偏好：本地特色、辣味、清淡、甜食、街边小吃、精致料理、夜市 等 */
    @Size(max = 5, message = "口味偏好最多选 5 项")
    private List<String> tastePreferences;

    /**
     * 预算档位：low(< 300/天) / medium(300-600/天) / high(> 600/天)
     */
    private String budget = "medium";

    /**
     * 出行方式：walking / driving / transit
     */
    private String transportMode = "transit";

    /**
     * 住宿类型：hotel / homestay / hostel / any
     */
    private String accommodationType = "hotel";

    private Integer adults = 2;
    private Integer children = 0;
}
