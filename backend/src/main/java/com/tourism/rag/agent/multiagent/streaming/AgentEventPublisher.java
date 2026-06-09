package com.tourism.rag.agent.multiagent.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.multiagent.core.AgentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Publishes {@link AgentEvent}s to an SSE {@link SseEmitter}.
 *
 * <p>Implements {@link Consumer}&lt;AgentEvent&gt; so it can be passed directly
 * to the orchestrator as an event sink.</p>
 */
@Slf4j
public class AgentEventPublisher implements Consumer<AgentEvent> {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;

    public AgentEventPublisher(SseEmitter emitter, ObjectMapper objectMapper) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(AgentEvent event) {
        if (event == null) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .id(event.getEventId() != null ? event.getEventId() : String.valueOf(System.currentTimeMillis()))
                    .name(event.getEventType())
                    .data(json));
        } catch (IOException e) {
            log.warn("[AgentEventPublisher] Failed to send SSE event: {}", e.getMessage());
            // Don't re-throw — the emitter will be completed by the controller
        }
    }

    /**
     * Send a heartbeat to keep the SSE connection alive.
     */
    public void heartbeat() {
        try {
            emitter.send(SseEmitter.event()
                    .name("HEARTBEAT")
                    .data("{\"type\":\"HEARTBEAT\",\"timestamp\":" + System.currentTimeMillis() + "}"));
        } catch (IOException ignored) {
            // Client may have disconnected
        }
    }
}
