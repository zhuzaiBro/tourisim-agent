package com.tourism.rag.agent.multiagent.communication;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a multi-agent debate session.
 */
@Data
@Builder
public class ConsensusResult {

    public enum Action {
        /** All agents agree, no changes needed */
        APPROVE,
        /** Majority suggests revisions — apply the suggested fixes */
        REVISE,
        /** Consensus is to reject — re-run the affected agent stage */
        REJECT
    }

    /** The agreed-upon action */
    private Action action;

    /** Confidence score (0.0–1.0) based on vote distribution */
    private double confidence;

    /** Number of participating agents */
    private int participantCount;

    /** Vote breakdown: vote (APPROVE/REVISE/REJECT) → count */
    private Map<String, Long> voteTally;

    /** Agent arguments from the debate */
    private List<DebateArgument> arguments;

    /** If action is REVISE, the specific revisions to apply */
    private String revisionInstructions;

    /** If action is REJECT, the reason and which agents to re-run */
    private String rejectReason;

    @Data
    @Builder
    public static class DebateArgument {
        private String agentId;
        private String agentName;
        private String argument;
        private String vote;
        private double confidence;
    }
}
