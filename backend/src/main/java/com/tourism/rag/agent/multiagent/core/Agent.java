package com.tourism.rag.agent.multiagent.core;

import java.util.List;

/**
 * Abstract base class for all specialist agents in the multi-agent system.
 *
 * <p>Each agent has:
 * <ul>
 *   <li>A unique ID and display name for the UI</li>
 *   <li>A persona that defines its expertise and communication style</li>
 *   <li>A set of tools it can use</li>
 *   <li>Dependencies — other agents that must complete before this one runs</li>
 * </ul>
 *
 * <p>Agents are Spring beans registered in {@link AgentRegistry}.
 * The orchestrator discovers them and schedules execution based on the dependency DAG.</p>
 */
public abstract class Agent {

    /**
     * Unique machine-readable identifier, e.g. "weather-analysis"
     */
    public abstract String agentId();

    /**
     * Human-readable name for the UI, e.g. "Weather Analysis Expert"
     */
    public abstract String displayName();

    /**
     * The persona that defines this agent's identity.
     */
    public abstract AgentPersona persona();

    /**
     * IDs of other agents this agent depends on.
     * The orchestrator will not execute this agent until all dependencies have completed.
     * Return an empty list for stage-1 agents with no dependencies.
     */
    public abstract List<String> dependencies();

    /**
     * Execute the agent's task within the given context.
     * The context provides the original request and results from dependency agents.
     *
     * @param ctx shared orchestration context
     * @return structured result with payload
     */
    public abstract AgentResult execute(AgentContext ctx);

    /**
     * Generate a fallback result when this agent fails or times out.
     * Default implementation returns a FAILED result, but agents can override
     * to provide degraded-but-functional output.
     */
    public AgentResult fallback(AgentContext ctx, Exception cause) {
        return AgentResult.failed(agentId(),
                cause != null ? cause.getMessage() : "Unknown error");
    }

    @Override
    public String toString() {
        return agentId() + " (" + displayName() + ")";
    }
}
