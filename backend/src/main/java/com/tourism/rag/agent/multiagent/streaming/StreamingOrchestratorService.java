package com.tourism.rag.agent.multiagent.streaming;

import com.tourism.rag.agent.multiagent.core.AgentEvent;
import com.tourism.rag.agent.multiagent.orchestration.OrchestratorService;
import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ItineraryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Streaming wrapper around {@link OrchestratorService}.
 *
 * <p>Delegates to the core orchestrator with an event sink that publishes
 * real-time agent activity via SSE.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingOrchestratorService {

    private final OrchestratorService orchestratorService;

    /**
     * Execute multi-agent itinerary generation with real-time streaming events.
     *
     * @param request   the itinerary request
     * @param userId    authenticated user ID (nullable)
     * @param eventSink consumer that receives agent lifecycle events
     * @return the completed itinerary response
     */
    public ItineraryResponse generateStreaming(ItineraryRequest request, Long userId,
                                                Consumer<AgentEvent> eventSink) {
        log.info("[StreamingOrchestrator] Starting streaming multi-agent generation for city={}",
                request.getCityCode());

        long start = System.currentTimeMillis();

        try {
            // Execute orchestration with event publishing
            ItineraryResponse response = orchestratorService.generateWithStreaming(
                    request, userId, eventSink);

            long duration = System.currentTimeMillis() - start;
            log.info("[StreamingOrchestrator] Complete in {}ms, itineraryId={}",
                    duration, response.getItineraryId());

            return response;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) {
                throw iae;
            }
            log.error("[StreamingOrchestrator] Failed", e);
            if (eventSink != null) {
                eventSink.accept(AgentEvent.builder()
                        .eventType("ORCHESTRATION_FAILED")
                        .summary("编排失败：" + e.getMessage())
                        .timestampMs(System.currentTimeMillis())
                        .build());
            }
            throw e;
        } catch (Exception e) {
            log.error("[StreamingOrchestrator] Failed", e);
            if (eventSink != null) {
                eventSink.accept(AgentEvent.builder()
                        .eventType("ORCHESTRATION_FAILED")
                        .summary("编排失败：" + e.getMessage())
                        .timestampMs(System.currentTimeMillis())
                        .build());
            }
            throw new RuntimeException("Multi-agent streaming generation failed: " + e.getMessage(), e);
        }
    }
}
