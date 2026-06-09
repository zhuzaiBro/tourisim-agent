package com.tourism.rag.dto.multiagent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * DTO for SSE events sent from the multi-agent system to the frontend.
 * Mirrors AgentEvent but flattened for frontend consumption.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStreamEvent {

    private String type;         // event type name
    private String eventId;
    private String agentId;
    private String agentName;
    private String stageName;
    private int stageNumber;
    private String summary;
    private String thought;
    private String toolName;
    private Map<String, Object> metadata;
    private long timestampMs;
    private long durationMs;
}
