package com.tourism.rag.agent.multiagent.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the multi-agent system.
 * Bound from application.yml under the "multi-agent" prefix.
 */
@Data
@Component
@ConfigurationProperties(prefix = "multi-agent")
public class MultiAgentConfig {

    /** Master switch to enable/disable multi-agent mode */
    private boolean enabled = true;

    /** Streaming configuration */
    private Streaming streaming = new Streaming();

    /** Per-agent timeout overrides */
    private Map<String, AgentConfig> agents = new HashMap<>();

    /** Debate configuration */
    private Debate debate = new Debate();

    @Data
    public static class Streaming {
        private long timeoutMs = 120_000;
    }

    @Data
    public static class AgentConfig {
        private long timeoutMs = 8_000;
        private int retryCount = 0;
    }

    @Data
    public static class Debate {
        private boolean enabled = true;
        private int maxRounds = 2;
        private double consensusThreshold = 0.5;
    }

    /**
     * Get timeout for a specific agent, falling back to default.
     */
    public long getAgentTimeoutMs(String agentId) {
        AgentConfig cfg = agents.get(agentId);
        return cfg != null ? cfg.getTimeoutMs() : 8_000L;
    }
}
