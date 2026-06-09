package com.tourism.rag.agent.multiagent.communication;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Structured inter-agent message for communication during orchestration.
 * Agents can query, suggest, warn, confirm, or object to each other's outputs.
 */
@Data
@Builder
public class AgentMessage {

    public enum Type {
        /** One agent asks another for specific information */
        QUERY,
        /** An agent proposes an improvement or alternative */
        SUGGESTION,
        /** An agent warns about a potential issue in another agent's output */
        WARNING,
        /** An agent confirms another agent's finding */
        CONFIRMATION,
        /** An agent objects to another agent's output (used in debate) */
        OBJECTION,
        /** Debate argument */
        ARGUMENT,
        /** Vote cast */
        VOTE
    }

    @Builder.Default
    private String messageId = UUID.randomUUID().toString().substring(0, 8);

    /** Sending agent ID */
    private String fromAgentId;

    /** Target agent ID (null = broadcast to all) */
    private String toAgentId;

    /** Message type */
    private Type type;

    /** Short subject line */
    private String subject;

    /** Human-readable content */
    private String content;

    /** Structured payload for programmatic consumption */
    private Map<String, Object> payload;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    // ---- Convenience factories ----

    public static AgentMessage suggestion(String from, String to, String subject, String content) {
        return AgentMessage.builder()
                .fromAgentId(from).toAgentId(to)
                .type(Type.SUGGESTION)
                .subject(subject).content(content)
                .build();
    }

    public static AgentMessage warning(String from, String to, String subject, String content) {
        return AgentMessage.builder()
                .fromAgentId(from).toAgentId(to)
                .type(Type.WARNING)
                .subject(subject).content(content)
                .build();
    }

    public static AgentMessage objection(String from, String to, String subject,
                                          String content, Map<String, Object> payload) {
        return AgentMessage.builder()
                .fromAgentId(from).toAgentId(to)
                .type(Type.OBJECTION)
                .subject(subject).content(content)
                .payload(payload)
                .build();
    }

    public static AgentMessage vote(String from, String vote, double confidence, String reason) {
        return AgentMessage.builder()
                .fromAgentId(from)
                .type(Type.VOTE)
                .content(vote)
                .payload(Map.of("vote", vote, "confidence", confidence, "reason", reason))
                .build();
    }
}
