package com.tourism.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.orchestration.OrchestratorService;
import com.tourism.rag.agent.multiagent.streaming.AgentEventPublisher;
import com.tourism.rag.agent.multiagent.streaming.StreamingOrchestratorService;
import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ItineraryResponse;
import com.tourism.rag.dto.multiagent.AgentInfo;
import com.tourism.rag.dto.multiagent.MultiAgentRequest;
import com.tourism.rag.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for the Multi-Agent Collaborative Intelligence system.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/multi-agent/itinerary — non-streaming generation</li>
 *   <li>POST /api/multi-agent/itinerary/stream — SSE streaming generation</li>
 *   <li>GET  /api/multi-agent/agents — list all registered agents</li>
 *   <li>GET  /api/multi-agent/status — system status</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/multi-agent")
@RequiredArgsConstructor
public class MultiAgentController {

    private final OrchestratorService orchestratorService;
    private final StreamingOrchestratorService streamingOrchestratorService;
    private final AgentRegistry agentRegistry;
    private final MultiAgentConfig config;
    private final ObjectMapper objectMapper;

    /**
     * Generate itinerary using multi-agent orchestration (non-streaming).
     */
    @PostMapping("/itinerary")
    public ResponseEntity<?> generateItinerary(
            @Valid @RequestBody MultiAgentRequest request,
            @AuthenticationPrincipal AuthUser currentUser) {

        Long userId = currentUser != null ? currentUser.getId() : null;
        log.info("[MultiAgentController] Non-streaming request city={}, userId={}",
                request.getCityCode(), userId);

        ItineraryRequest agentReq = toItineraryRequest(request);
        orchestratorService.validateRequest(agentReq);

        ItineraryResponse response = orchestratorService.generate(agentReq, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate itinerary with real-time multi-agent SSE streaming.
     *
     * <p>The SSE stream emits typed events:
     * ORCHESTRATION_STARTED → STAGE_STARTED → AGENT_STARTED → AGENT_THINKING →
     * AGENT_TOOL_CALL → AGENT_COMPLETED → STAGE_COMPLETED → ...
     * → FINAL_RESULT</p>
     */
    @PostMapping(value = "/itinerary/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamItinerary(
            @Valid @RequestBody MultiAgentRequest request,
            @AuthenticationPrincipal AuthUser currentUser) {

        Long userId = currentUser != null ? currentUser.getId() : null;
        long timeout = config.getStreaming().getTimeoutMs();

        ItineraryRequest agentReq = toItineraryRequest(request);
        // 同步校验：在返回 SseEmitter 前失败，以便 GlobalExceptionHandler 返回 JSON 400
        orchestratorService.validateRequest(agentReq);

        SseEmitter emitter = new SseEmitter(timeout);
        AgentEventPublisher publisher = new AgentEventPublisher(emitter, objectMapper);

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        heartbeat.scheduleAtFixedRate(publisher::heartbeat, 15, 15, TimeUnit.SECONDS);

        Thread.startVirtualThread(() -> {
            try {
                ItineraryResponse response = streamingOrchestratorService.generateStreaming(
                        agentReq, userId, publisher);

                emitter.send(SseEmitter.event()
                        .name("FINAL_RESULT")
                        .data(objectMapper.writeValueAsString(response)));

                emitter.complete();
            } catch (Exception e) {
                log.error("[MultiAgentController] Streaming orchestration error", e);
                try {
                    String msg = e.getMessage() != null ? e.getMessage() : "编排失败";
                    emitter.send(SseEmitter.event()
                            .name("ERROR")
                            .data(objectMapper.writeValueAsString(Map.of("error", msg))));
                    emitter.complete();
                } catch (IOException io) {
                    log.warn("[MultiAgentController] Failed to send SSE error event", io);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                }
            } finally {
                heartbeat.shutdown();
            }
        });

        emitter.onCompletion(heartbeat::shutdown);
        emitter.onTimeout(heartbeat::shutdown);
        emitter.onError(e -> heartbeat.shutdown());

        return emitter;
    }

    /**
     * List all registered multi-agent agents with their metadata.
     */
    @GetMapping("/agents")
    public ResponseEntity<?> listAgents() {
        List<AgentInfo> agents = agentRegistry.getAll().stream()
                .map(agent -> AgentInfo.builder()
                        .agentId(agent.agentId())
                        .displayName(agent.displayName())
                        .roleDescription(agent.persona().getRoleDescription())
                        .style(agent.persona().getStyle())
                        .icon(agent.persona().getIcon())
                        .dependencies(agent.dependencies())
                        .toolNames(List.of()) // tools populated per agent
                        .build())
                .toList();

        return ResponseEntity.ok(Map.of(
                "agents", agents,
                "totalCount", agents.size(),
                "enabled", config.isEnabled()
        ));
    }

    /**
     * Multi-agent system status.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "mode", "multi-agent",
                "version", "2.0",
                "agentsRegistered", agentRegistry.size(),
                "debateEnabled", config.getDebate().isEnabled(),
                "streamingTimeoutMs", config.getStreaming().getTimeoutMs(),
                "endpoints", Map.of(
                        "generate", "POST /api/multi-agent/itinerary",
                        "stream", "POST /api/multi-agent/itinerary/stream",
                        "agents", "GET  /api/multi-agent/agents",
                        "status", "GET  /api/multi-agent/status"
                )
        ));
    }

    // ---- Helpers ----

    private ItineraryRequest toItineraryRequest(MultiAgentRequest req) {
        ItineraryRequest r = new ItineraryRequest();
        r.setCityCode(req.getCityCode());
        r.setCityName(req.getCityName());
        r.setStartDate(req.getStartDate());
        r.setEndDate(req.getEndDate());
        r.setPreferences(req.getPreferences());
        r.setDietaryRestrictions(req.getDietaryRestrictions());
        r.setTastePreferences(req.getTastePreferences());
        r.setBudget(req.getBudget());
        r.setTransportMode(req.getTransportMode());
        r.setAccommodationType(req.getAccommodationType());
        r.setAdults(req.getAdults());
        r.setChildren(req.getChildren());
        return r;
    }

    private String escapeJson(String s) {
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
