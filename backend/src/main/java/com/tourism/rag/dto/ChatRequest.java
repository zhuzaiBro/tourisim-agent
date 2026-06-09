package com.tourism.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 聊天请求 DTO
 */
@Data
public class ChatRequest {

    /** 会话 ID（用于多轮对话记忆，前端生成 UUID） */
    private String sessionId;

    /**
     * 城市编码，可传多个实现跨城市查询
     * 例如：["qingdao"] 或 ["qingdao", "beijing"]（联游场景）
     * 为空时全城市检索
     */
    private List<String> cities;

    /** 用户问题 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过2000字")
    private String question;

    /**
     * 用户偏好（可选，用于个性化行程规划）
     * 例如：{"type": "family", "budget": "中等", "days": 3}
     */
    private TravelPreferences preferences;

    /**
     * 知识分类过滤（可选）
     * 取值：attraction / food / transport / accommodation / festival / knowledge
     * 为空时不按分类过滤
     */
    private String category;

    /** 是否流式输出（SSE） */
    private boolean stream = false;

    @Data
    public static class TravelPreferences {
        private String type;         // family / couple / food / photography / elderly / backpacker
        private String budget;       // 经济 / 中等 / 豪华
        private Integer days;        // 行程天数
        private List<String> interests; // 具体兴趣标签
    }
}
