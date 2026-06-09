package com.tourism.rag.agent.multiagent.core;

import lombok.Builder;
import lombok.Data;

/**
 * Defines an agent's identity, communication style, and system prompt.
 * Each specialist agent has a distinct persona to create the multi-agent "team" feel.
 */
@Data
@Builder
public class AgentPersona {

    /** Unique agent identifier, e.g. "weather-analysis" */
    private String agentId;

    /** Human-readable display name for the UI, e.g. "Weather Analysis Specialist" */
    private String displayName;

    /** One-line role description, e.g. "Meteorology expert for travel planning" */
    private String roleDescription;

    /** Communication style: "data-driven", "enthusiastic", "precise", "creative", "rigorous" */
    private String style;

    /** Emoji/icon for the agent in the UI */
    private String icon;

    /** LLM system prompt (used by LLM-based agents) */
    private String systemPrompt;
}
