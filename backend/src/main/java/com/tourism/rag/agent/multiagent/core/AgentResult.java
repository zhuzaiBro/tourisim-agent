package com.tourism.rag.agent.multiagent.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Typed result from any agent execution, carrying a flexible payload
 * and rich metadata for debugging and streaming.
 */
@Data
@Builder
public class AgentResult {

    private String agentId;
    private AgentStatus status;
    private String summary;          // human-readable one-liner for the UI

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    private long durationMs;
    private String errorMessage;
    private boolean usedFallback;
    private String fallbackReason;

    @Builder.Default
    private String completedAt = Instant.now().toString();

    // ---- Convenience factories ----

    public static AgentResult success(String agentId, String summary) {
        return AgentResult.builder()
                .agentId(agentId)
                .status(AgentStatus.COMPLETED)
                .summary(summary)
                .build();
    }

    public static AgentResult success(String agentId, String summary, Map<String, Object> payload) {
        return AgentResult.builder()
                .agentId(agentId)
                .status(AgentStatus.COMPLETED)
                .summary(summary)
                .payload(payload)
                .build();
    }

    public static AgentResult failed(String agentId, String errorMessage) {
        return AgentResult.builder()
                .agentId(agentId)
                .status(AgentStatus.FAILED)
                .errorMessage(errorMessage)
                .summary("执行失败：" + errorMessage)
                .build();
    }

    public static AgentResult fallback(String agentId, String reason, Map<String, Object> payload) {
        return AgentResult.builder()
                .agentId(agentId)
                .status(AgentStatus.FALLBACK)
                .summary("降级处理：" + reason)
                .payload(payload)
                .usedFallback(true)
                .fallbackReason(reason)
                .build();
    }

    // ---- Typed payload accessors ----

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) payload.get(key);
    }

    public AgentResult put(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }
}
