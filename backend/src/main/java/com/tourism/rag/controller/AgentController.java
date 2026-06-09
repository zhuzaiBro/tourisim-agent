package com.tourism.rag.controller;

import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ItineraryResponse;
import com.tourism.rag.security.AuthUser;
import com.tourism.rag.service.ItineraryAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能行程 Agent REST 端点（Legacy 单 Agent 模式）。
 *
 * <p>推荐使用 {@link com.tourism.rag.controller.MultiAgentController} 的
 * {@code POST /api/multi-agent/itinerary} 或流式 {@code /itinerary/stream}。
 * 本端点保留用于兼容与对比测试。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final ItineraryAgentService agentService;

    /**
     * 生成智能行程。
     * 支持匿名访问（不保存 userId）和已登录访问（关联 userId）。
     */
    @PostMapping("/itinerary")
    public ResponseEntity<?> generateItinerary(
            @Valid @RequestBody ItineraryRequest request,
            @AuthenticationPrincipal AuthUser currentUser) {

        Long userId = currentUser != null ? currentUser.getId() : null;
        log.info("[AgentController] generateItinerary city={}, userId={}",
                request.getCityCode(), userId);

        ItineraryResponse response = agentService.generate(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 当前用户的历史行程列表（分页）。
     */
    @GetMapping("/itineraries")
    public ResponseEntity<?> listItineraries(
            @AuthenticationPrincipal AuthUser currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "需要登录"));
        }
        return ResponseEntity.ok(agentService.listByUser(currentUser.getId(), page, size));
    }

    /**
     * 根据 ID 查询历史行程（已保存）。
     */
    @GetMapping("/itinerary/{id}")
    public ResponseEntity<?> getItinerary(@PathVariable String id) {
        return agentService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 健康检查 + Agent 配置摘要。
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "agent", "itinerary",
                "version", "2.0",
                "mode", "single-agent",  // legacy mode; multi-agent at /api/multi-agent
                "multiAgentAvailable", true,
                "endpoints", Map.of(
                        "generate",     "POST /api/agent/itinerary",
                        "getById",      "GET  /api/agent/itinerary/{id}",
                        "status",       "GET  /api/agent/status",
                        "multiAgent",   "POST /api/multi-agent/itinerary",
                        "multiStream",  "POST /api/multi-agent/itinerary/stream",
                        "multiAgents",  "GET  /api/multi-agent/agents",
                        "multiStatus",  "GET  /api/multi-agent/status"
                )
        ));
    }
}
