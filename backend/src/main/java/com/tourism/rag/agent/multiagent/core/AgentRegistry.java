package com.tourism.rag.agent.multiagent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring-managed registry of all {@link Agent} beans.
 * Agents are auto-discovered via Spring constructor injection
 * (all Agent subclasses are @Component).
 *
 * <p>Provides lookup by agentId for the orchestrator's dependency resolution.</p>
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * Spring injects all Agent beans into this constructor.
     */
    public AgentRegistry(List<Agent> agentList) {
        for (Agent agent : agentList) {
            agents.put(agent.agentId(), agent);
            log.info("[AgentRegistry] Registered: {} — {}", agent.agentId(), agent.displayName());
        }
        log.info("[AgentRegistry] Total agents registered: {}", agents.size());
    }

    public Agent get(String agentId) {
        Agent agent = agents.get(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + agentId);
        }
        return agent;
    }

    public Optional<Agent> find(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public Collection<Agent> getAll() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public int size() {
        return agents.size();
    }
}
