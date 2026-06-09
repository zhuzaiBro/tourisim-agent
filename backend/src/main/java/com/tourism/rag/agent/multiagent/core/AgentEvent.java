package com.tourism.rag.agent.multiagent.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tourism.rag.agent.multiagent.communication.ConsensusResult;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lifecycle event emitted during agent orchestration.
 * Serialized as an SSE event for frontend consumption.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentEvent {

    private String eventId;
    private String eventType;      // matches StreamEventType enum values
    private String agentId;
    private String agentName;
    private String stageName;
    private int stageNumber;

    private String summary;         // human-readable event description
    private String thought;         // agent internal reasoning (AGENT_THINKING)
    private String toolName;        // tool being called (AGENT_TOOL_CALL)
    private Map<String, Object> metadata;  // extensible payload

    private long timestampMs;
    private long durationMs;

    @Builder.Default
    private String emittedAt = Instant.now().toString();

    public static AgentEvent orchestrationStarted(String requestId, String cityName, int totalStages) {
        return AgentEvent.builder()
                .eventId(requestId + "-orch-start")
                .eventType("ORCHESTRATION_STARTED")
                .summary("多智能体编排已启动：" + cityName)
                .metadata(Map.of("requestId", requestId, "cityName", cityName, "totalStages", totalStages))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent stageStarted(int stageNum, String stageName, int agentCount) {
        return AgentEvent.builder()
                .eventType("STAGE_STARTED")
                .stageNumber(stageNum)
                .stageName(stageName)
                .summary(String.format("第%d阶段：%s（%d个智能体）", stageNum, stageName, agentCount))
                .metadata(Map.of("agentCount", agentCount))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent stageCompleted(int stageNum, String stageName, long durationMs) {
        return AgentEvent.builder()
                .eventType("STAGE_COMPLETED")
                .stageNumber(stageNum)
                .stageName(stageName)
                .summary(String.format("第%d阶段完成，耗时%dms", stageNum, durationMs))
                .durationMs(durationMs)
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent agentStarted(String agentId, String agentName, int stageNum) {
        return AgentEvent.builder()
                .eventId(agentId + "-start")
                .eventType("AGENT_STARTED")
                .agentId(agentId)
                .agentName(agentName)
                .stageNumber(stageNum)
                .summary(agentName + " 已启动")
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent agentThinking(String agentId, String thought) {
        return AgentEvent.builder()
                .eventId(agentId + "-think")
                .eventType("AGENT_THINKING")
                .agentId(agentId)
                .thought(thought)
                .summary("思考：" + truncate(thought, 80))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent agentToolCall(String agentId, String toolName, Map<String, Object> params) {
        return AgentEvent.builder()
                .eventId(agentId + "-tool")
                .eventType("AGENT_TOOL_CALL")
                .agentId(agentId)
                .toolName(toolName)
                .metadata(params)
                .summary("调用工具：" + toolName)
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent agentCompleted(String agentId, String agentName, String summary, long durationMs) {
        return AgentEvent.builder()
                .eventId(agentId + "-done")
                .eventType("AGENT_COMPLETED")
                .agentId(agentId)
                .agentName(agentName)
                .summary(summary)
                .durationMs(durationMs)
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent agentFailed(String agentId, String agentName, String error, boolean fallback) {
        return AgentEvent.builder()
                .eventId(agentId + "-fail")
                .eventType(fallback ? "AGENT_FALLBACK" : "AGENT_FAILED")
                .agentId(agentId)
                .agentName(agentName)
                .summary(error)
                .metadata(Map.of("fallbackActivated", fallback))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent debateInitiated(String issue, List<String> participants) {
        return AgentEvent.builder()
                .eventType("DEBATE_INITIATED")
                .summary("辩论议题：" + issue)
                .metadata(Map.of(
                        "issue", issue,
                        "participantCount", participants.size(),
                        "participants", participants))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent debateArgument(String agentId, String agentName,
                                             String argument, String vote,
                                             double confidence, int round) {
        return AgentEvent.builder()
                .eventType("DEBATE_ARGUMENT")
                .agentId(agentId)
                .agentName(agentName)
                .summary(agentName + "：" + truncate(argument, 60))
                .metadata(Map.of(
                        "argument", argument,
                        "vote", vote,
                        "confidence", confidence,
                        "round", round))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent voteCast(String agentId, String agentName, String vote, double confidence) {
        return AgentEvent.builder()
                .eventType("VOTE_CAST")
                .agentId(agentId)
                .agentName(agentName)
                .summary(agentName + " 投票 " + vote)
                .metadata(Map.of("vote", vote, "confidence", confidence))
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent consensusReached(ConsensusResult result) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("action", result.getAction().name());
        meta.put("confidence", result.getConfidence());
        meta.put("voteTally", result.getVoteTally());
        meta.put("arguments", result.getArguments());
        meta.put("revisionInstructions", result.getRevisionInstructions());
        meta.put("rejectReason", result.getRejectReason());
        meta.put("participantCount", result.getParticipantCount());

        return AgentEvent.builder()
                .eventType("CONSENSUS_REACHED")
                .summary("达成共识：" + result.getAction().name()
                        + "（置信度：" + String.format("%.2f", result.getConfidence()) + "）")
                .metadata(meta)
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent finalResult(String itineraryId, long totalDurationMs) {
        return AgentEvent.builder()
                .eventType("FINAL_RESULT")
                .summary("行程已生成：" + itineraryId)
                .metadata(Map.of("itineraryId", itineraryId, "totalDurationMs", totalDurationMs))
                .durationMs(totalDurationMs)
                .timestampMs(System.currentTimeMillis())
                .build();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
