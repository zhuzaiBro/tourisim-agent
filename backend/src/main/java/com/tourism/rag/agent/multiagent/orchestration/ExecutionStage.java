package com.tourism.rag.agent.multiagent.orchestration;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A group of agents that can execute in parallel within one orchestration stage.
 * Stages are executed sequentially; agents within a stage run concurrently.
 */
@Data
@Builder
public class ExecutionStage {

    /** 1-based stage number */
    private int stageNumber;

    /** Human-readable stage name for the UI */
    private String stageName;

    /** Agent IDs in this stage (all run in parallel) */
    private List<String> agentIds;

    /** Whether this stage can start (all dependencies resolved) */
    private boolean ready;

    /** Whether this stage has completed */
    private boolean completed;

    /** Wall-clock duration of this stage in ms */
    private long durationMs;

    public int agentCount() {
        return agentIds.size();
    }
}
