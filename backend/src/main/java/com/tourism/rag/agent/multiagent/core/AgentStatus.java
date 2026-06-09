package com.tourism.rag.agent.multiagent.core;

/**
 * Agent lifecycle status for multi-agent orchestration.
 */
public enum AgentStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
    FALLBACK,
    DEBATING,
    VOTING
}
