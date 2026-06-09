package com.tourism.rag.agent.multiagent.streaming;

/**
 * All SSE event types emitted during multi-agent orchestration.
 * These are the named events in the SSE stream that the frontend listens for.
 */
public enum StreamEventType {

    // ── Orchestration lifecycle ──
    ORCHESTRATION_STARTED,
    STAGE_STARTED,
    STAGE_COMPLETED,
    ORCHESTRATION_COMPLETED,
    ORCHESTRATION_FAILED,

    // ── Agent lifecycle ──
    AGENT_STARTED,
    AGENT_THINKING,
    AGENT_TOOL_CALL,
    AGENT_TOOL_RESULT,
    AGENT_COMPLETED,
    AGENT_FAILED,
    AGENT_FALLBACK,

    // ── Inter-agent communication ──
    AGENT_MESSAGE,

    // ── Debate / voting ──
    DEBATE_INITIATED,
    DEBATE_ARGUMENT,
    VOTE_CAST,
    CONSENSUS_REACHED,

    // ── Result delivery ──
    DAY_PLAN_PARTIAL,
    FINAL_RESULT,

    // ── System ──
    HEARTBEAT,
    ERROR
}
