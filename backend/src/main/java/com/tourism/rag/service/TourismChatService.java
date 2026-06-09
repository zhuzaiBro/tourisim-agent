package com.tourism.rag.service;

import com.tourism.rag.dto.ChatRequest;
import com.tourism.rag.dto.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourismChatService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final RetrievalService retrievalService;

    @Autowired(required = false)
    private ConversationService conversationService;

    @Value("${rag.system-prompt}")
    private String systemPromptTemplate;

    private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES_PER_SESSION = 20;

    // ============================================================
    // 非流式对话
    // ============================================================

    public ChatResponse chat(ChatRequest request, Long userId) {
        String sessionId = getOrCreateSessionId(request.getSessionId());
        List<String> cities = request.getCities();

        log.info("RAG 对话 - session: {}, userId: {}, cities: {}", sessionId, userId, cities);

        List<Content> retrievedContents = retrieveForChat(request);

        String groundingContext = buildGroundingContext(retrievedContents);
        String augmentedUserMessage = buildAugmentedPrompt(
                request.getQuestion(), groundingContext, request.getPreferences(), cities);

        ChatMemory memory = getOrCreateMemory(sessionId);
        List<ChatMessage> messages = buildMessageList(memory, augmentedUserMessage);

        Response<AiMessage> response = chatLanguageModel.generate(messages);
        String answer = response.content().text();

        memory.add(UserMessage.from(request.getQuestion()));
        memory.add(response.content());

        List<ChatResponse.SourceReference> sources = extractSources(retrievedContents);

        // Persist to DB if user is authenticated
        if (userId != null && conversationService != null) {
            try {
                conversationService.saveMessages(sessionId, userId,
                        request.getQuestion(), answer, sources);
            } catch (Exception e) {
                log.warn("保存消息到数据库失败: {}", e.getMessage());
            }
        }

        return ChatResponse.builder()
                .sessionId(sessionId)
                .answer(answer)
                .sources(sources)
                .retrievedChunks(retrievedContents.size())
                .filteredCities(cities)
                .build();
    }

    // ============================================================
    // 流式对话（SSE）
    // ============================================================

    public Flux<String> chatStream(ChatRequest request, Long userId) {
        String sessionId = getOrCreateSessionId(request.getSessionId());
        List<String> cities = request.getCities();

        return Flux.create(sink -> {
            try {
                List<Content> retrievedContents = retrieveForChat(request);

                String groundingContext = buildGroundingContext(retrievedContents);
                String augmentedPrompt = buildAugmentedPrompt(
                        request.getQuestion(), groundingContext, request.getPreferences(), cities);

                ChatMemory memory = getOrCreateMemory(sessionId);
                List<ChatMessage> messages = buildMessageList(memory, augmentedPrompt);

                StringBuilder fullResponse = new StringBuilder();
                List<ChatResponse.SourceReference> sources = extractSources(retrievedContents);

                streamingChatLanguageModel.generate(messages,
                        new dev.langchain4j.model.StreamingResponseHandler<AiMessage>() {
                            @Override
                            public void onNext(String token) {
                                fullResponse.append(token);
                                sink.next(token);
                            }

                            @Override
                            public void onComplete(Response<AiMessage> response) {
                                memory.add(UserMessage.from(request.getQuestion()));
                                memory.add(response.content());

                                if (!sources.isEmpty()) {
                                    String sourceStr = "\n\n---\n**参考来源：**\n" +
                                            sources.stream()
                                                    .map(s -> "- 【" + s.getSource() + "】" +
                                                              (s.getExcerpt() != null ? " " + s.getExcerpt() : ""))
                                                    .collect(Collectors.joining("\n"));
                                    sink.next(sourceStr);
                                }

                                // Persist to DB
                                if (userId != null && conversationService != null) {
                                    try {
                                        conversationService.saveMessages(sessionId, userId,
                                                request.getQuestion(), fullResponse.toString(), sources);
                                    } catch (Exception e) {
                                        log.warn("流式响应保存消息失败: {}", e.getMessage());
                                    }
                                }

                                sink.next("[DONE]");
                                sink.complete();
                            }

                            @Override
                            public void onError(Throwable error) {
                                log.error("流式响应错误", error);
                                sink.error(error);
                            }
                        });
            } catch (Exception e) {
                log.error("流式对话异常", e);
                sink.error(e);
            }
        });
    }

    // ============================================================
    // 内部方法
    // ============================================================

    private String buildGroundingContext(List<Content> contents) {
        if (contents.isEmpty()) {
            return "（未找到相关参考资料，请告知用户暂无数据）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是检索到的相关旅游资料（请基于这些内容回答）：\n\n");

        for (int i = 0; i < contents.size(); i++) {
            TextSegment segment = contents.get(i).textSegment();
            String city = segment.metadata().getString("city");
            String category = segment.metadata().getString("category");
            String source = segment.metadata().getString("source");

            sb.append(String.format("[参考%d] 城市:%s | 分类:%s | 来源:%s\n",
                    i + 1, city, category, source));
            sb.append(segment.text());
            sb.append("\n\n");
        }

        return sb.toString();
    }

    private String buildAugmentedPrompt(String question, String context,
                                         ChatRequest.TravelPreferences preferences,
                                         List<String> cities) {
        StringBuilder sb = new StringBuilder();

        if (preferences != null) {
            sb.append("【用户偏好】\n");
            if (preferences.getType() != null) sb.append("- 旅行类型：").append(preferences.getType()).append("\n");
            if (preferences.getBudget() != null) sb.append("- 预算级别：").append(preferences.getBudget()).append("\n");
            if (preferences.getDays() != null) sb.append("- 旅行天数：").append(preferences.getDays()).append("天\n");
            if (preferences.getInterests() != null && !preferences.getInterests().isEmpty()) {
                sb.append("- 兴趣爱好：").append(String.join("、", preferences.getInterests())).append("\n");
            }
            sb.append("\n");
        }

        sb.append(context).append("\n");
        sb.append("【用户问题】\n").append(question).append("\n\n");
        sb.append("【回答要求】\n");
        sb.append("1. 严格基于上述参考资料回答，不得添加参考资料中没有的信息\n");
        sb.append("2. 回答中必须标注信息来源，格式：【来源：具体来源名称】\n");
        sb.append("3. 如果参考资料不足以完整回答，请明确说明哪些信息缺失\n");
        sb.append("4. 使用 Markdown 格式，条理清晰\n");

        return sb.toString();
    }

    private List<ChatMessage> buildMessageList(ChatMemory memory, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPromptTemplate));
        messages.addAll(memory.messages());
        messages.add(UserMessage.from(userMessage));
        return messages;
    }

    private List<ChatResponse.SourceReference> extractSources(List<Content> contents) {
        return contents.stream().map(content -> {
            TextSegment segment = content.textSegment();
            String text = segment.text();
            String excerpt = text.length() > 100 ? text.substring(0, 100) + "..." : text;

            return ChatResponse.SourceReference.builder()
                    .source(segment.metadata().getString("source"))
                    .city(segment.metadata().getString("city"))
                    .category(segment.metadata().getString("category"))
                    .excerpt(excerpt)
                    .build();
        }).toList();
    }

    private String getOrCreateSessionId(String sessionId) {
        return (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
    }

    private List<Content> retrieveForChat(ChatRequest request) {
        String query = request.getQuestion();
        List<String> cities = request.getCities();
        String category = ChatRetrievalHelper.normalizeCategory(request.getCategory());

        if (ChatRetrievalHelper.isCrossCityComparison(query)) {
            log.info("RAG 检索: global（跨城对比）");
            return retrievalService.retrieveGlobal(query);
        }
        if (category != null) {
            log.info("RAG 检索: category={}, cities={}", category, cities);
            return retrievalService.retrieveWithCategoryFilter(query, cities, category);
        }
        log.info("RAG 检索: city-filter, cities={}", cities);
        return retrievalService.retrieveWithCityFilter(query, cities);
    }

    private ChatMemory getOrCreateMemory(String sessionId) {
        return sessionMemories.computeIfAbsent(sessionId, id -> {
            ChatMemory memory = MessageWindowChatMemory.builder()
                    .id(id)
                    .maxMessages(MAX_MESSAGES_PER_SESSION)
                    .build();
            if (conversationService != null) {
                try {
                    List<ChatMessage> history =
                            conversationService.loadChatMemoryMessages(id, MAX_MESSAGES_PER_SESSION);
                    for (ChatMessage msg : history) {
                        memory.add(msg);
                    }
                    if (!history.isEmpty()) {
                        log.info("从数据库恢复会话记忆 sessionId={}, messages={}", id, history.size());
                    }
                } catch (Exception e) {
                    log.debug("会话记忆恢复跳过 sessionId={}: {}", id, e.getMessage());
                }
            }
            return memory;
        });
    }

    public void clearSession(String sessionId) {
        sessionMemories.remove(sessionId);
        log.info("已清除会话记忆: {}", sessionId);
    }
}
