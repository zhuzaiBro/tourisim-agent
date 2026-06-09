package com.tourism.rag.agent.multiagent.specialists;

import com.tourism.rag.agent.multiagent.communication.CrossValidator;
import com.tourism.rag.agent.multiagent.communication.ConsensusResult;
import com.tourism.rag.agent.multiagent.core.*;
import com.tourism.rag.agent.multiagent.persona.PersonaLibrary;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Quality assurance agent that cross-validates all other agents' outputs.
 *
 * <p>Stage 4 (sequential after NarrativeGenerationAgent) — depends on all agents.
 * Runs cross-validation rules and triggers debate if issues are found.</p>
 */
@Slf4j
@Component
public class SafetyValidationAgent extends Agent {

    private final CrossValidator crossValidator;
    private final ChatLanguageModel chatLanguageModel;

    public SafetyValidationAgent(@Lazy CrossValidator crossValidator,
                                  ChatLanguageModel chatLanguageModel) {
        this.crossValidator = crossValidator;
        this.chatLanguageModel = chatLanguageModel;
    }

    @Override
    public String agentId() {
        return "safety-validation";
    }

    @Override
    public String displayName() {
        return "Quality Assurance Expert";
    }

    @Override
    public AgentPersona persona() {
        return PersonaLibrary.safetyValidation();
    }

    @Override
    public List<String> dependencies() {
        return List.of("narrative-generation", "day-scheduling", "budget-planning");
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        Map<String, AgentResult> allResults = ctx.getAllResults();

        // Step 1: Run cross-validation
        List<String> issues = crossValidator.validate(allResults, ctx);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issuesFound", issues);
        payload.put("issueCount", issues.size());
        payload.put("allClear", issues.isEmpty());

        if (issues.isEmpty()) {
            payload.put("verdict", "APPROVED — All checks passed");
            payload.put("qualityScore", 1.0);

            // Generate quality report via LLM
            String report = generateQualityReport(ctx, issues);
            payload.put("qualityReport", report);

            return AgentResult.success(agentId(),
                    "All validations passed. Itinerary is feasible and safe.", payload);
        }

        // Step 2: Issues found — generate findings and recommendations
        List<String> recommendations = generateRecommendations(issues, ctx);
        payload.put("recommendations", recommendations);

        // Per-day tips from validation
        for (int day = 1; day <= ctx.getTotalDays(); day++) {
            List<String> dayTips = buildDayValidationTips(day, issues, ctx);
            payload.put("day" + day + "_tips", dayTips);
        }

        String summary = String.format("Found %d issues, provided %d recommendations",
                issues.size(), recommendations.size());

        // Still return success — issues don't mean failure, just warnings
        return AgentResult.builder()
                .agentId(agentId())
                .status(AgentStatus.COMPLETED)
                .summary(summary)
                .payload(payload)
                .build();
    }

    /**
     * When issues are found, attempt to resolve them via the debate protocol.
     * Called externally by the orchestrator if debate is enabled.
     */
    public ConsensusResult runDebate(AgentContext ctx, Consumer<AgentEvent> eventSink,
                                      double threshold, int maxRounds) {
        return crossValidator.validateWithDebate(
                ctx.getAllResults(), ctx, eventSink, threshold, maxRounds);
    }

    private String generateQualityReport(AgentContext ctx, List<String> issues) {
        try {
            String prompt = String.format("""
                    你是行程质量审核专家。请用50字以内的中文总结以下验证结果：

                    城市：%s
                    天数：%d
                    发现问题：%d个
                    %s

                    如果没有问题，给出正面评价。如果有问题，简要说明。
                    """,
                    ctx.getCityName(),
                    ctx.getTotalDays(),
                    issues.size(),
                    issues.isEmpty() ? "无问题，行程质量优秀" : "问题：" + String.join("；", issues));

            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            return issues.isEmpty()
                    ? "行程质量审核通过，各项指标正常。祝旅途愉快！"
                    : "发现" + issues.size() + "个需要注意的问题，已提供优化建议。";
        }
    }

    private List<String> generateRecommendations(List<String> issues, AgentContext ctx) {
        List<String> recs = new ArrayList<>();
        for (String issue : issues) {
            if (issue.contains("exceeds 12 hours")) {
                recs.add("建议减少当天景点数量，或延长行程天数以分摊游览时间");
            } else if (issue.contains("too light")) {
                recs.add("当天行程较轻松，可考虑添加周边小众景点或特色体验活动");
            } else if (issue.contains("outdoor activities on a")) {
                recs.add("雨天建议切换到备选（室内）方案，提升游览舒适度");
            } else if (issue.contains("Budget")) {
                recs.add("预算超出建议范围，可选择部分免费景点或调整餐饮档次以控制花费");
            } else if (issue.contains("Route leg")) {
                recs.add("路线跨度较大，建议中间增加休息点或调整景点顺序缩短单段通行时间");
            }
        }
        if (recs.isEmpty()) {
            recs.add("行程整体合理，无需调整");
        }
        return recs;
    }

    private List<String> buildDayValidationTips(int day, List<String> issues, AgentContext ctx) {
        List<String> tips = new ArrayList<>();
        for (String issue : issues) {
            if (issue.contains("第" + day + "天：")) {
                tips.add("⚠️ " + issue.substring(("第" + day + "天：").length()));
            }
        }
        if (tips.isEmpty()) {
            tips.add("✅ 第" + day + "天行程审核通过，安排合理");
        }
        return tips;
    }
}
