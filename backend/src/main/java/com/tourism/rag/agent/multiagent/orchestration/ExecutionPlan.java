package com.tourism.rag.agent.multiagent.orchestration;

import com.tourism.rag.agent.multiagent.core.Agent;
import com.tourism.rag.agent.multiagent.core.AgentRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a DAG-based execution plan from agent dependency declarations.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Collect all agents and their dependency lists</li>
 *   <li>Topologically sort agents into stages</li>
 *   <li>Agents in the same stage have no dependencies on each other</li>
 *   <li>Each agent's dependencies are satisfied by agents in earlier stages</li>
 * </ol>
 *
 * <p>The resulting plan is a list of {@link ExecutionStage}s to be executed sequentially,
 * with agents within each stage running in parallel.</p>
 */
@Slf4j
public class ExecutionPlan {

    private final List<ExecutionStage> stages;
    private final Set<String> allAgentIds;
    private final AgentRegistry registry;

    public ExecutionPlan(AgentRegistry registry, List<String> participatingAgentIds) {
        this.registry = registry;
        this.allAgentIds = new LinkedHashSet<>(participatingAgentIds);
        this.stages = buildStages(participatingAgentIds);
    }

    /**
     * Topological sort into stages using Kahn-like breadth-first layering.
     */
    private List<ExecutionStage> buildStages(List<String> agentIds) {
        // inDegree: how many unsatisfied dependencies an agent has
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>(); // reverse edges

        for (String id : agentIds) {
            Agent agent = registry.get(id);
            List<String> deps = agent.dependencies().stream()
                    .filter(agentIds::contains) // only consider participating agents
                    .collect(Collectors.toList());
            inDegree.put(id, deps.size());
            for (String dep : deps) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(id);
            }
        }

        List<ExecutionStage> result = new ArrayList<>();
        Queue<String> ready = new LinkedList<>();
        Set<String> completed = new HashSet<>();

        // First layer: agents with no dependencies
        for (String id : agentIds) {
            if (inDegree.getOrDefault(id, 0) == 0) {
                ready.add(id);
            }
        }

        int stageNum = 1;
        while (!ready.isEmpty()) {
            List<String> currentStageAgents = new ArrayList<>(ready);
            ready.clear();

            String stageName = buildStageName(stageNum, currentStageAgents);
            result.add(ExecutionStage.builder()
                    .stageNumber(stageNum)
                    .stageName(stageName)
                    .agentIds(currentStageAgents)
                    .ready(true)
                    .completed(false)
                    .build());

            completed.addAll(currentStageAgents);

            // Resolve dependencies for remaining agents
            for (String agentId : currentStageAgents) {
                for (String dependent : dependents.getOrDefault(agentId, List.of())) {
                    int remaining = inDegree.merge(dependent, -1, Integer::sum);
                    if (remaining == 0) {
                        ready.add(dependent);
                    }
                }
            }

            stageNum++;
        }

        // Check for cycles or unresolved agents
        if (completed.size() < agentIds.size()) {
            List<String> unresolved = agentIds.stream()
                    .filter(id -> !completed.contains(id))
                    .toList();
            log.warn("[ExecutionPlan] Unresolved agents (possible cycle or missing dependency): {}",
                    unresolved);
            // Add them all in a final stage as a fallback
            if (!unresolved.isEmpty()) {
                result.add(ExecutionStage.builder()
                        .stageNumber(stageNum)
                        .stageName("降级处理")
                        .agentIds(unresolved)
                        .ready(true)
                        .completed(false)
                        .build());
            }
        }

        log.info("[ExecutionPlan] Built {} stages for {} agents", result.size(), agentIds.size());
        for (ExecutionStage stage : result) {
            log.info("[ExecutionPlan]   Stage {}: {} — agents: {}",
                    stage.getStageNumber(), stage.getStageName(), stage.getAgentIds());
        }

        return result;
    }

    private String buildStageName(int num, List<String> agentIds) {
        Agent first = registry.get(agentIds.get(0));
        if (agentIds.size() == 1) {
            return first.displayName();
        }
        return "并行：" + agentIds.stream()
                .map(id -> registry.get(id).displayName())
                .collect(Collectors.joining(" + "));
    }

    public List<ExecutionStage> getStages() {
        return Collections.unmodifiableList(stages);
    }

    public int getTotalStages() {
        return stages.size();
    }

    public Set<String> getAllAgentIds() {
        return Collections.unmodifiableSet(allAgentIds);
    }

    /**
     * Get the dependency-ordered list of all agent IDs across all stages.
     */
    public List<String> getAgentOrder() {
        return stages.stream()
                .flatMap(s -> s.getAgentIds().stream())
                .toList();
    }
}
