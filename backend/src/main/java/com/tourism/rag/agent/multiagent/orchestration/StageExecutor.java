package com.tourism.rag.agent.multiagent.orchestration;

import com.tourism.rag.agent.multiagent.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Executes a single orchestration stage by running all its agents in parallel
 * using JDK 21 virtual threads.
 *
 * <p>Each agent gets its own virtual thread, timeout, and fallback handler.
 * If one agent fails, others in the same stage continue unaffected.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageExecutor {

    private final AgentRegistry agentRegistry;
    private final MultiAgentConfig config;

    /**
     * Execute all agents in a stage concurrently.
     *
     * @param stage       the stage to execute
     * @param ctx         shared orchestration context
     * @param eventSink   optional consumer for streaming events (null = no streaming)
     * @return map of agentId → AgentResult
     */
    public Map<String, AgentResult> execute(ExecutionStage stage, AgentContext ctx,
                                            Consumer<AgentEvent> eventSink) {
        log.info("[StageExecutor] Starting Stage {}: {} ({} agents)",
                stage.getStageNumber(), stage.getStageName(), stage.agentCount());

        long stageStart = System.currentTimeMillis();

        if (eventSink != null) {
            eventSink.accept(AgentEvent.stageStarted(
                    stage.getStageNumber(), stage.getStageName(), stage.agentCount()));
        }

        Map<String, AgentResult> results = new ConcurrentHashMap<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AgentResult>> futures = new ArrayList<>();

            for (String agentId : stage.getAgentIds()) {
                futures.add(executor.submit(() ->
                        executeSingleAgent(agentId, ctx, stage.getStageNumber(), eventSink)));
            }

            // Collect results with per-stage timeout
            long stageTimeoutMs = stage.getAgentIds().stream()
                    .mapToLong(config::getAgentTimeoutMs)
                    .max()
                    .orElse(30_000L);

            for (int i = 0; i < stage.getAgentIds().size(); i++) {
                String agentId = stage.getAgentIds().get(i);
                try {
                    AgentResult result = futures.get(i).get(stageTimeoutMs, TimeUnit.MILLISECONDS);
                    results.put(agentId, result);
                    ctx.putResult(agentId, result);
                } catch (TimeoutException e) {
                    log.warn("[StageExecutor] Agent {} timed out after {}ms", agentId, stageTimeoutMs);
                    AgentResult fallback = handleTimeout(agentId, ctx, eventSink);
                    results.put(agentId, fallback);
                    ctx.putResult(agentId, fallback);
                } catch (ExecutionException e) {
                    log.error("[StageExecutor] Agent {} threw exception", agentId, e.getCause());
                    AgentResult fallback = handleFailure(agentId, ctx, (Exception) e.getCause(), eventSink);
                    results.put(agentId, fallback);
                    ctx.putResult(agentId, fallback);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[StageExecutor] Interrupted while waiting for agent {}", agentId);
                    AgentResult fallback = handleFailure(agentId, ctx, e, eventSink);
                    results.put(agentId, fallback);
                    ctx.putResult(agentId, fallback);
                }
            }
        }

        long duration = System.currentTimeMillis() - stageStart;
        stage.setDurationMs(duration);
        stage.setCompleted(true);

        if (eventSink != null) {
            eventSink.accept(AgentEvent.stageCompleted(
                    stage.getStageNumber(), stage.getStageName(), duration));
        }

        log.info("[StageExecutor] Stage {} completed in {}ms. Results: {}/{} success",
                stage.getStageNumber(), duration,
                results.values().stream().filter(r -> r.getStatus() == AgentStatus.COMPLETED).count(),
                results.size());

        return results;
    }

    private AgentResult executeSingleAgent(String agentId, AgentContext ctx,
                                            int stageNum, Consumer<AgentEvent> eventSink) {
        Agent agent = agentRegistry.get(agentId);
        long agentTimeout = config.getAgentTimeoutMs(agentId);
        long start = System.currentTimeMillis();

        log.info("[StageExecutor] Agent '{}' starting (timeout: {}ms)", agentId, agentTimeout);

        if (eventSink != null) {
            eventSink.accept(AgentEvent.agentStarted(agentId, agent.displayName(), stageNum));
            eventSink.accept(AgentEvent.agentThinking(agentId,
                    "正在分析" + ctx.getCityName() + "的旅行数据…"));
        }

        try {
            AgentResult result = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return agent.execute(ctx);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    })
                    .orTimeout(agentTimeout, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        if (cause instanceof TimeoutException) {
                            log.warn("[StageExecutor] Agent '{}' timed out", agentId);
                            return agent.fallback(ctx, new TimeoutException(
                                    "Agent '" + agentId + "' timed out after " + agentTimeout + "ms"));
                        }
                        log.error("[StageExecutor] Agent '{}' failed", agentId, cause);
                        return agent.fallback(ctx, cause instanceof Exception
                                ? (Exception) cause : new RuntimeException(cause));
                    })
                    .get();

            long duration = System.currentTimeMillis() - start;
            result.setDurationMs(duration);

            if (eventSink != null) {
                if (result.getStatus() == AgentStatus.FALLBACK) {
                    eventSink.accept(AgentEvent.agentFailed(agentId, agent.displayName(),
                            result.getFallbackReason(), true));
                } else {
                    eventSink.accept(AgentEvent.agentCompleted(agentId, agent.displayName(),
                            result.getSummary(), duration));
                }
            }

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[StageExecutor] Unexpected error executing agent '{}'", agentId, e);
            AgentResult fallback = agent.fallback(ctx, e);
            fallback.setDurationMs(duration);
            return fallback;
        }
    }

    private AgentResult handleTimeout(String agentId, AgentContext ctx,
                                       Consumer<AgentEvent> eventSink) {
        Agent agent = agentRegistry.get(agentId);
        AgentResult fallback = agent.fallback(ctx,
                new TimeoutException("Agent timed out"));
        if (eventSink != null) {
            eventSink.accept(AgentEvent.agentFailed(agentId, agent.displayName(),
                    "超时 — 使用降级数据", true));
        }
        return fallback;
    }

    private AgentResult handleFailure(String agentId, AgentContext ctx,
                                       Exception cause, Consumer<AgentEvent> eventSink) {
        Agent agent = agentRegistry.get(agentId);
        AgentResult fallback = agent.fallback(ctx, cause);
        if (eventSink != null) {
            eventSink.accept(AgentEvent.agentFailed(agentId, agent.displayName(),
                    cause.getMessage() != null ? cause.getMessage() : "Unknown error", true));
        }
        return fallback;
    }
}
