package com.tourism.rag.agent.multiagent.communication;

import com.tourism.rag.agent.multiagent.core.Agent;
import com.tourism.rag.agent.multiagent.core.AgentContext;
import com.tourism.rag.agent.multiagent.core.AgentEvent;
import com.tourism.rag.agent.multiagent.core.AgentRegistry;
import com.tourism.rag.agent.multiagent.core.AgentResult;
import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.WeatherInfo;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * Manages a single multi-agent debate session with LLM-generated arguments and votes.
 */
@Slf4j
public class DebateSession {

    private final String issue;
    private final List<String> participantAgentIds;
    private final AgentRegistry registry;
    private final AgentContext ctx;
    private final Consumer<AgentEvent> eventSink;
    private final double consensusThreshold;
    private final int maxRounds;
    private final ChatLanguageModel chatLanguageModel;
    private final String contextSummary;

    private final List<ConsensusResult.DebateArgument> arguments = new ArrayList<>();
    private final Map<String, String> votes = new LinkedHashMap<>();
    private final Map<String, Double> voteConfidences = new LinkedHashMap<>();

    public DebateSession(String issue, List<String> participantIds,
                         AgentRegistry registry, AgentContext ctx,
                         Consumer<AgentEvent> eventSink,
                         double consensusThreshold, int maxRounds,
                         ChatLanguageModel chatLanguageModel) {
        this.issue = issue;
        this.participantAgentIds = participantIds;
        this.registry = registry;
        this.ctx = ctx;
        this.eventSink = eventSink;
        this.consensusThreshold = consensusThreshold;
        this.maxRounds = maxRounds;
        this.chatLanguageModel = chatLanguageModel;
        this.contextSummary = buildContextSummary(ctx);
    }

    public ConsensusResult run() {
        log.info("[DebateSession] Starting debate on: {} ({} participants, max {} rounds)",
                issue, participantAgentIds.size(), maxRounds);

        if (eventSink != null) {
            eventSink.accept(AgentEvent.debateInitiated(issue, participantAgentIds));
        }

        for (int round = 1; round <= maxRounds; round++) {
            log.info("[DebateSession] Round {} / {}", round, maxRounds);
            votes.clear();
            voteConfidences.clear();

            for (String agentId : participantAgentIds) {
                Agent agent = registry.get(agentId);
                String argument = generateDebateArgument(agent, round);
                String vote = castVote(agent, argument, round);
                double confidence = voteConfidence(vote);

                arguments.add(ConsensusResult.DebateArgument.builder()
                        .agentId(agentId)
                        .agentName(agent.displayName())
                        .argument(argument)
                        .vote(vote)
                        .confidence(confidence)
                        .build());

                votes.put(agentId, vote);
                voteConfidences.put(agentId, confidence);

                if (eventSink != null) {
                    eventSink.accept(AgentEvent.debateArgument(
                            agentId, agent.displayName(), argument, vote, confidence, round));
                    eventSink.accept(AgentEvent.voteCast(agentId, agent.displayName(), vote, confidence));
                }

                log.info("[DebateSession] {} votes {} (confidence: {}) — {}",
                        agent.displayName(), vote, confidence, argument);
            }

            Map<String, Long> tally = new LinkedHashMap<>();
            for (String vote : votes.values()) {
                tally.merge(vote, 1L, Long::sum);
            }

            long approveCount = tally.getOrDefault("APPROVE", 0L);
            long totalVotes = votes.size();
            double approveRatio = totalVotes > 0 ? (double) approveCount / totalVotes : 0;

            if (approveRatio >= consensusThreshold) {
                ConsensusResult result = buildConsensus(ConsensusResult.Action.APPROVE, tally,
                        "达成共识：" + (int) (approveRatio * 100) + "% 通过");
                emitConsensus(result);
                return result;
            }

            long reviseCount = tally.getOrDefault("REVISE", 0L);
            if (approveCount + reviseCount >= totalVotes * 0.5) {
                String instructions = buildRevisionInstructions(arguments);
                ConsensusResult result = buildConsensus(ConsensusResult.Action.REVISE, tally, instructions);
                emitConsensus(result);
                return result;
            }

            if (round >= maxRounds) {
                ConsensusResult result = buildConsensus(ConsensusResult.Action.REJECT, tally,
                        "未能达成共识，已进行" + maxRounds + "轮辩论。以审核专家意见为准。");
                emitConsensus(result);
                return result;
            }
        }

        return buildConsensus(ConsensusResult.Action.REJECT, Map.of(), "辩论未能达成共识。");
    }

    private String generateDebateArgument(Agent agent, int round) {
        try {
            String prompt = String.format("""
                    你是「%s」（%s），正在参与行程质量辩论（第%d轮）。
                    议题：%s
                    行程上下文：
                    %s
                    请用80字以内中文陈述你的专业观点，必须引用上述具体景点或天气信息（如有）。
                    只输出论点正文，不要标题或前缀。
                    """,
                    agent.displayName(),
                    agent.persona().getRoleDescription(),
                    round,
                    issue,
                    contextSummary);
            String response = chatLanguageModel.generate(prompt);
            if (response != null && !response.isBlank()) {
                return response.trim().lines().findFirst().orElse(response.trim());
            }
        } catch (Exception e) {
            log.warn("[DebateSession] LLM argument failed for {}: {}", agent.agentId(), e.getMessage());
        }
        return fallbackArgument(agent, round);
    }

    private String castVote(Agent agent, String argument, int round) {
        try {
            String prompt = String.format("""
                    你是「%s」。议题：%s
                    你的论点：%s
                    上下文：%s
                    请根据议题严重程度，只输出一个词：APPROVE、REVISE 或 REJECT。
                    """,
                    agent.displayName(), issue, argument, contextSummary);
            String response = chatLanguageModel.generate(prompt);
            if (response != null) {
                String upper = response.trim().toUpperCase();
                if (upper.contains("REJECT")) return "REJECT";
                if (upper.contains("REVISE")) return "REVISE";
                if (upper.contains("APPROVE")) return "APPROVE";
            }
        } catch (Exception e) {
            log.warn("[DebateSession] LLM vote failed for {}: {}", agent.agentId(), e.getMessage());
        }
        return fallbackVote(agent);
    }

    private double voteConfidence(String vote) {
        return switch (vote) {
            case "APPROVE" -> 0.88;
            case "REVISE" -> 0.82;
            case "REJECT" -> 0.78;
            default -> 0.75;
        };
    }

    private String fallbackArgument(Agent agent, int round) {
        return switch (agent.agentId()) {
            case "safety-validation" -> "检测到问题：" + issue + "。建议修改以确保质量和安全。";
            case "day-scheduling" -> "行程" + (round == 1 ? "可压缩景点时长" : "需进一步精简") + "以满足12小时上限。";
            case "route-optimization" -> "可优化路线减少通行时间，降低单日总时长。";
            case "budget-planning" -> "预算影响可控，但建议优先调整免费景点比例。";
            case "weather-analysis" -> "结合天气预报，雨天应增加室内备选方案。";
            default -> "建议审慎评估后修订行程。";
        };
    }

    private String fallbackVote(Agent agent) {
        return switch (agent.agentId()) {
            case "safety-validation" -> "REVISE";
            case "day-scheduling", "route-optimization" -> "REVISE";
            default -> "APPROVE";
        };
    }

    private String buildRevisionInstructions(List<ConsensusResult.DebateArgument> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据智能体反馈，应用以下修改：\n");
        for (var arg : args) {
            if ("REVISE".equals(arg.getVote()) || "REJECT".equals(arg.getVote())) {
                sb.append("- [").append(arg.getAgentName()).append("]: ").append(arg.getArgument()).append("\n");
            }
        }
        if (issue.contains("超过12小时") || issue.contains("活动时间")) {
            sb.append("- 压缩每日景点数量与游览时长，确保总活动时间≤12小时\n");
        }
        if (issue.contains("路段") || issue.contains("耗时")) {
            sb.append("- 减少每日景点数或优化路线顺序\n");
        }
        return sb.toString();
    }

    private ConsensusResult buildConsensus(ConsensusResult.Action action,
                                          Map<String, Long> tally, String detail) {
        double avgConfidence = voteConfidences.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        return ConsensusResult.builder()
                .action(action)
                .confidence(avgConfidence)
                .participantCount(participantAgentIds.size())
                .voteTally(tally)
                .arguments(List.copyOf(arguments))
                .revisionInstructions(action == ConsensusResult.Action.REVISE ? detail : null)
                .rejectReason(action == ConsensusResult.Action.REJECT ? detail : null)
                .build();
    }

    private void emitConsensus(ConsensusResult result) {
        if (eventSink != null) {
            eventSink.accept(AgentEvent.consensusReached(result));
        }
    }

    private static String buildContextSummary(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("城市：").append(ctx.getCityName()).append("，").append(ctx.getTotalDays()).append("天\n");

        AgentResult weatherResult = ctx.getResult("weather-analysis");
        if (weatherResult != null && weatherResult.getPayload() != null) {
            @SuppressWarnings("unchecked")
            List<WeatherInfo> weatherList = (List<WeatherInfo>) weatherResult.getPayload().get("weatherList");
            if (weatherList != null) {
                for (WeatherInfo w : weatherList) {
                    sb.append("天气 ").append(w.getDate()).append("：")
                            .append(w.getConditionText())
                            .append("，").append(w.getTempLow()).append("-").append(w.getTempHigh()).append("℃\n");
                }
            }
        }

        AgentResult poiResult = ctx.getResult("poi-discovery");
        if (poiResult != null && poiResult.getPayload() != null) {
            @SuppressWarnings("unchecked")
            List<PoiInfo> pois = (List<PoiInfo>) poiResult.getPayload().get("poiList");
            if (pois != null) {
                pois.stream().limit(6).forEach(p ->
                        sb.append("景点：").append(p.getName())
                                .append(p.isIndoorVenue() ? "（室内）" : "（户外）")
                                .append("\n"));
            }
        }
        return sb.toString();
    }
}
