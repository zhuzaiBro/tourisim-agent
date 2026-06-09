package com.tourism.rag.dto.multiagent;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO for debate-related SSE events.
 */
@Data
@Builder
public class DebateEvent {

    private String issue;
    private int participantCount;
    private List<String> participantIds;
    private int roundNumber;
    private int maxRounds;

    private String agentId;
    private String agentName;
    private String argument;
    private String vote;
    private double confidence;

    private String consensusAction;
    private double consensusConfidence;
    private Map<String, Long> voteTally;
    private String revisionInstructions;
}
