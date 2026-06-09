package com.tourism.rag.agent.multiagent.core;

import com.tourism.rag.dto.agent.ItineraryRequest;
import com.tourism.rag.dto.agent.ToolCallLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared blackboard for multi-agent orchestration.
 * Holds the original request, intermediate results from all agents,
 * computed derived data (e.g., date ranges, city center), and an event audit trail.
 *
 * <p>Thread-safe: uses ConcurrentHashMap for results, volatile for stage tracking.</p>
 */
public class AgentContext {

    private final String requestId;
    private final ItineraryRequest originalRequest;
    private final ConcurrentHashMap<String, AgentResult> results;
    private final String cityName;
    private final List<LocalDate> dates;
    private volatile int currentStage;
    private final Map<String, Object> sharedState;
    private final List<ToolCallLog> toolCallLogs = Collections.synchronizedList(new ArrayList<>());

    public AgentContext(String requestId, ItineraryRequest request,
                        String cityName, List<LocalDate> dates) {
        this.requestId = requestId;
        this.originalRequest = request;
        this.cityName = cityName;
        this.dates = dates;
        this.results = new ConcurrentHashMap<>();
        this.sharedState = new ConcurrentHashMap<>();
        this.currentStage = 0;
    }

    // ---- Result management ----

    public void putResult(String agentId, AgentResult result) {
        results.put(agentId, result);
    }

    public AgentResult getResult(String agentId) {
        return results.get(agentId);
    }

    public Map<String, AgentResult> getAllResults() {
        return Map.copyOf(results);
    }

    public boolean hasResult(String agentId) {
        return results.containsKey(agentId);
    }

    // ---- Stage tracking ----

    public int getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(int stage) {
        this.currentStage = stage;
    }

    // ---- Shared state (for agents to pass data) ----

    public void putState(String key, Object value) {
        sharedState.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getState(String key) {
        return (T) sharedState.get(key);
    }

    // ---- Getters ----

    public String getRequestId() {
        return requestId;
    }

    public ItineraryRequest getRequest() {
        return originalRequest;
    }

    public String getCityName() {
        return cityName;
    }

    public List<LocalDate> getDates() {
        return dates;
    }

    public int getTotalDays() {
        return dates.size();
    }

    public List<ToolCallLog> getToolCallLogs() {
        return toolCallLogs;
    }
}
