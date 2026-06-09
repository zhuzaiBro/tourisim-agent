package com.tourism.rag.agent.multiagent.communication;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory publish/subscribe message bus for agent-to-agent communication.
 *
 * <p>Agents can:
 * <ul>
 *   <li>Subscribe to messages directed at them (by agentId)</li>
 *   <li>Subscribe to broadcast messages (no specific target)</li>
 *   <li>Publish messages to specific agents or broadcast</li>
 * </ul>
 *
 * <p>All messages are also accumulated in a history log for the debate protocol.</p>
 */
@Slf4j
@Component
public class MessageBus {

    private final Map<String, List<Consumer<AgentMessage>>> subscriptions = new ConcurrentHashMap<>();
    private final List<AgentMessage> messageHistory = new CopyOnWriteArrayList<>();

    /**
     * Subscribe to messages directed at a specific agent.
     */
    public void subscribe(String agentId, Consumer<AgentMessage> handler) {
        subscriptions.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Subscribe to all messages (broadcast + directed).
     */
    public void subscribeAll(Consumer<AgentMessage> handler) {
        subscriptions.computeIfAbsent("*", k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Publish a message. If toAgentId is null, it's a broadcast.
     */
    public void publish(AgentMessage message) {
        messageHistory.add(message);

        // Deliver to target
        if (message.getToAgentId() != null) {
            List<Consumer<AgentMessage>> handlers = subscriptions.get(message.getToAgentId());
            if (handlers != null) {
                for (Consumer<AgentMessage> h : handlers) {
                    try {
                        h.accept(message);
                    } catch (Exception e) {
                        log.warn("[MessageBus] Handler error for {}: {}", message.getToAgentId(), e.getMessage());
                    }
                }
            }
        }

        // Deliver to broadcast listeners
        List<Consumer<AgentMessage>> allHandlers = subscriptions.get("*");
        if (allHandlers != null) {
            for (Consumer<AgentMessage> h : allHandlers) {
                try {
                    h.accept(message);
                } catch (Exception e) {
                    log.warn("[MessageBus] Broadcast handler error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Get all messages exchanged so far (for the debate protocol).
     */
    public List<AgentMessage> getHistory() {
        return List.copyOf(messageHistory);
    }

    /**
     * Get messages from/to a specific agent.
     */
    public List<AgentMessage> getHistory(String agentId) {
        return messageHistory.stream()
                .filter(m -> agentId.equals(m.getFromAgentId()) || agentId.equals(m.getToAgentId()))
                .toList();
    }

    /**
     * Clear all subscriptions and history (called between orchestration runs).
     */
    public void reset() {
        subscriptions.clear();
        messageHistory.clear();
    }
}
