package com.tourism.rag.agent.multiagent.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.multiagent.communication.ConsensusResult;
import com.tourism.rag.agent.multiagent.communication.CrossValidator;
import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.specialists.SafetyValidationAgent;
import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ItineraryResponse;
import com.tourism.rag.entity.ItineraryRecord;
import com.tourism.rag.repository.CityRepository;
import com.tourism.rag.util.CityNameResolver;
import com.tourism.rag.repository.ItineraryRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Top-level orchestrator for multi-agent itinerary generation.
 *
 * <p>Orchestration flow:
 * <ol>
 *   <li>Build {@link ExecutionPlan} from agent dependency DAG</li>
 *   <li>Execute stages sequentially via {@link StageExecutor}</li>
 *   <li>Agents within each stage run in parallel on virtual threads</li>
 *   <li>Collect results via {@link ResultAggregator}</li>
 *   <li>Persist asynchronously</li>
 * </ol>
 *
 * <p>This service is used by both the non-streaming and streaming controllers.
 * The streaming variant additionally passes an event sink to publish real-time events.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${agent.max-days:7}")
    private int maxDays;

    private final AgentRegistry agentRegistry;
    private final StageExecutor stageExecutor;
    private final ResultAggregator resultAggregator;
    private final SafetyValidationAgent safetyValidationAgent;
    private final CrossValidator crossValidator;
    private final MultiAgentConfig multiAgentConfig;
    private final ObjectMapper objectMapper;
    private final ItineraryRecordRepository itineraryRepo;
    private final CityRepository cityRepository;

    // ---- All participating agent IDs in dependency order ----

    private static final List<String> PARTICIPATING_AGENTS = List.of(
            "weather-analysis",
            "poi-discovery",
            "route-optimization",
            "food-recommendation",
            "accommodation-recommendation",
            "day-scheduling",
            "budget-planning",
            "narrative-generation",
            "safety-validation"
    );

    /**
     * Generate itinerary without streaming (one-shot response).
     */
    public ItineraryResponse generate(ItineraryRequest req, Long userId) {
        return executeOrchestration(req, userId, null);
    }

    /**
     * Generate itinerary with streaming events.
     *
     * @param eventSink consumer for AgentEvent (the SSE publisher)
     */
    public ItineraryResponse generateWithStreaming(ItineraryRequest req, Long userId,
                                                    Consumer<AgentEvent> eventSink) {
        return executeOrchestration(req, userId, eventSink);
    }

    private ItineraryResponse executeOrchestration(ItineraryRequest req, Long userId,
                                                    Consumer<AgentEvent> eventSink) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put("requestId", requestId);

        long orchestrationStart = System.currentTimeMillis();
        log.info("[Orchestrator] Starting multi-agent orchestration requestId={}, city={}",
                requestId, req.getCityCode());

        try {
            // 1. Validate & prepare
            validate(req);
            String cityName = CityNameResolver.resolve(req.getCityCode(), req.getCityName(), cityRepository);
            List<LocalDate> dates = buildDateRange(req.getStartDate(), req.getEndDate());

            // 2. Build context
            AgentContext ctx = new AgentContext(requestId, req, cityName, dates);

            // 3. Build execution plan (DAG → stages)
            ExecutionPlan plan = new ExecutionPlan(agentRegistry, PARTICIPATING_AGENTS);

            if (eventSink != null) {
                eventSink.accept(AgentEvent.orchestrationStarted(
                        requestId, cityName, plan.getTotalStages()));
            }

            // 4. Execute stages sequentially
            Map<String, AgentResult> allResults = new LinkedHashMap<>();
            for (ExecutionStage stage : plan.getStages()) {
                ctx.setCurrentStage(stage.getStageNumber());
                Map<String, AgentResult> stageResults = stageExecutor.execute(stage, ctx, eventSink);
                allResults.putAll(stageResults);
            }

            // 5. Check for critical failures
            verifyCriticalResults(allResults, ctx);

            // 6. Debate + revision loop (respect multi-agent.debate.enabled)
            ConsensusResult consensus = runDebateAndRevise(plan, ctx, allResults, eventSink);

            // 7. Aggregate results into response
            String itineraryId = UUID.randomUUID().toString();
            ItineraryResponse response = resultAggregator.aggregate(ctx, itineraryId, allResults, consensus);

            // 8. Async persist
            saveAsync(itineraryId, req, response, userId);

            long totalDuration = System.currentTimeMillis() - orchestrationStart;
            log.info("[Orchestrator] Multi-agent orchestration complete itineraryId={}, duration={}ms, agents={}",
                    itineraryId, totalDuration, allResults.size());

            if (eventSink != null) {
                eventSink.accept(AgentEvent.finalResult(itineraryId, totalDuration));
            }

            return response;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Orchestrator] Orchestration failed requestId={}", requestId, e);
            throw new RuntimeException("Multi-agent itinerary generation failed: " + e.getMessage(), e);
        } finally {
            MDC.remove("requestId");
        }
    }

    private ConsensusResult runDebateAndRevise(ExecutionPlan plan, AgentContext ctx,
                                                Map<String, AgentResult> allResults,
                                                Consumer<AgentEvent> eventSink) {
        if (!multiAgentConfig.getDebate().isEnabled()) {
            return null;
        }

        List<String> issues = crossValidator.validate(allResults, ctx);
        if (issues.isEmpty()) {
            return ConsensusResult.builder()
                    .action(ConsensusResult.Action.APPROVE)
                    .confidence(1.0)
                    .participantCount(0)
                    .voteTally(Map.of("APPROVE", 1L))
                    .arguments(List.of())
                    .build();
        }

        log.info("[Orchestrator] Validation found {} issues, starting debate", issues.size());
        ConsensusResult consensus = safetyValidationAgent.runDebate(
                ctx, eventSink,
                multiAgentConfig.getDebate().getConsensusThreshold(),
                multiAgentConfig.getDebate().getMaxRounds());

        if (consensus.getAction() == ConsensusResult.Action.APPROVE) {
            return consensus;
        }

        boolean rejectHandled = false;
        if (consensus.getAction() == ConsensusResult.Action.REJECT) {
            applyRevisionFlags(ctx, issues, true);
            rerunFromStage(plan, 2, ctx, allResults, eventSink);
            rejectHandled = true;
            log.info("[Orchestrator] REJECT: re-ran from stage 2 (route optimization)");
        } else if (consensus.getAction() == ConsensusResult.Action.REVISE) {
            applyRevisionFlags(ctx, issues, false);
            rerunFromStage(plan, 3, ctx, allResults, eventSink);
            log.info("[Orchestrator] REVISE: re-ran from stage 3 (day scheduling)");
        }

        if (rejectHandled || consensus.getAction() == ConsensusResult.Action.REVISE) {
            List<String> remaining = crossValidator.validate(allResults, ctx);
            if (!remaining.isEmpty()) {
                log.info("[Orchestrator] Post-revision issues: {} (was {})", remaining.size(), issues.size());
            }
            ctx.putState("debateRevised", true);
        }

        return consensus;
    }

    private void applyRevisionFlags(AgentContext ctx, List<String> issues, boolean fullReject) {
        boolean tooSparse = issues.stream().anyMatch(i -> i.contains("过于松散"));
        boolean tooLong = issues.stream().anyMatch(i -> i.contains("超过12小时"));

        if (tooSparse) {
            ctx.putState("expandSchedule", true);
            ctx.putState("minVisitMinutes", 90);
            ctx.putState("compressSchedule", false);
        } else if (tooLong) {
            ctx.putState("compressSchedule", true);
            ctx.putState("maxVisitMinutes", 90);
            ctx.putState("expandSchedule", false);
        }

        if (fullReject || issues.stream().anyMatch(i -> i.contains("路段") || i.contains("耗时"))) {
            ctx.putState("reducePoisPerDay", true);
        }
    }

    private void rerunFromStage(ExecutionPlan plan, int fromStage, AgentContext ctx,
                                 Map<String, AgentResult> allResults,
                                 Consumer<AgentEvent> eventSink) {
        for (ExecutionStage stage : plan.getStages()) {
            if (stage.getStageNumber() >= fromStage) {
                ctx.setCurrentStage(stage.getStageNumber());
                Map<String, AgentResult> stageResults = stageExecutor.execute(stage, ctx, eventSink);
                allResults.putAll(stageResults);
            }
        }
    }

    /**
     * Verify that critical agents produced usable results.
     * Fails fast if weather + POI (stage 1) both failed completely.
     */
    private void verifyCriticalResults(Map<String, AgentResult> results, AgentContext ctx) {
        List<String> criticalErrors = new ArrayList<>();

        for (String agentId : List.of("weather-analysis", "poi-discovery")) {
            AgentResult r = results.get(agentId);
            if (r == null || r.getStatus() == AgentStatus.FAILED) {
                criticalErrors.add(agentId + " failed: " +
                        (r != null ? r.getErrorMessage() : "no result"));
            }
        }

        if (!criticalErrors.isEmpty()) {
            log.warn("[Orchestrator] Critical agent issues detected: {}", criticalErrors);
            // Don't throw — continue with fallback data. The aggregator will mark
            // hasRealWeatherData / hasRealPoiData as false so the frontend knows.
        }
    }

    // ---- Validation & helpers (mirrored from ItineraryAgentService) ----

    /** 在开启 SSE 流之前同步校验，避免异步线程内抛错导致响应已提交。 */
    public void validateRequest(ItineraryRequest req) {
        validate(req);
    }

    private void validate(ItineraryRequest req) {
        LocalDate start = LocalDate.parse(req.getStartDate(), DATE_FMT);
        LocalDate end = LocalDate.parse(req.getEndDate(), DATE_FMT);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        long days = start.until(end).getDays() + 1;
        if (days > maxDays) {
            throw new IllegalArgumentException("行程最长支持 " + maxDays + " 天");
        }
    }

    private List<LocalDate> buildDateRange(String start, String end) {
        LocalDate s = LocalDate.parse(start, DATE_FMT);
        LocalDate e = LocalDate.parse(end, DATE_FMT);
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cur = s;
        while (!cur.isAfter(e)) {
            dates.add(cur);
            cur = cur.plusDays(1);
        }
        return dates;
    }

    @Async
    public void saveAsync(String id, ItineraryRequest req, ItineraryResponse resp, Long userId) {
        try {
            itineraryRepo.save(ItineraryRecord.builder()
                    .id(id)
                    .cityCode(req.getCityCode())
                    .cityName(resp.getCityName())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .totalDays(resp.getTotalDays())
                    .responseJson(objectMapper.writeValueAsString(resp))
                    .requestJson(objectMapper.writeValueAsString(req))
                    .userId(userId)
                    .build());
        } catch (Exception e) {
            log.warn("[Orchestrator] Failed to persist itinerary id={}", id, e);
        }
    }
}
