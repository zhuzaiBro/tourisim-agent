package com.tourism.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.ChatResponse.SourceReference;
import com.tourism.rag.dto.ConversationDto;
import com.tourism.rag.dto.ConversationDto.MessageDto;
import com.tourism.rag.entity.Conversation;
import com.tourism.rag.entity.ConversationMessage;
import com.tourism.rag.repository.ConversationMessageRepository;
import com.tourism.rag.repository.ConversationRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    // ---- Conversation CRUD ----

    public ConversationDto createConversation(Long userId, String title, List<String> cities) {
        String id = UUID.randomUUID().toString();
        Conversation conv = Conversation.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .citiesJson(toJson(cities))
                .build();
        conv = conversationRepository.save(conv);
        return toDto(conv);
    }

    public List<ConversationDto> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ConversationDto updateTitle(String conversationId, Long userId, String newTitle) {
        Conversation conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
        conv.setTitle(newTitle);
        return toDto(conversationRepository.save(conv));
    }

    @Transactional
    public void deleteConversation(String conversationId, Long userId) {
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteByIdAndUserId(conversationId, userId);
    }

    public List<MessageDto> getMessages(String conversationId, Long userId) {
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("对话不存在"));
        return messageRepository.findByConversationIdOrderByTimestampAsc(conversationId)
                .stream().map(this::toMsgDto).toList();
    }

    // ---- Message persistence (called by chat service) ----

    @Transactional
    public void saveMessages(String conversationId, Long userId,
                             String userContent, String assistantContent,
                             List<SourceReference> sources) {
        // Update conversation updatedAt
        conversationRepository.findByIdAndUserId(conversationId, userId).ifPresent(conv -> {
            conv.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conv);
        });

        LocalDateTime now = LocalDateTime.now();

        ConversationMessage userMsg = ConversationMessage.builder()
                .conversationId(conversationId)
                .role("user")
                .content(userContent)
                .timestamp(now)
                .build();
        messageRepository.save(userMsg);

        ConversationMessage assistantMsg = ConversationMessage.builder()
                .conversationId(conversationId)
                .role("assistant")
                .content(assistantContent)
                .sourcesJson(toJson(sources))
                .timestamp(now.plusNanos(1))
                .build();
        messageRepository.save(assistantMsg);
    }

    @Transactional
    public void clearMessages(String conversationId, Long userId) {
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .ifPresent(c -> messageRepository.deleteByConversationId(conversationId));
    }

    /**
     * 从数据库恢复 LangChain4j 会话记忆（与 ConversationMessage 统一存储）。
     */
    public List<ChatMessage> loadChatMemoryMessages(String conversationId, int maxMessages) {
        if (!conversationRepository.existsById(conversationId)) {
            return List.of();
        }
        List<ConversationMessage> records =
                messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        List<ChatMessage> messages = new ArrayList<>();
        for (ConversationMessage record : records) {
            if ("user".equals(record.getRole())) {
                messages.add(UserMessage.from(record.getContent()));
            } else if ("assistant".equals(record.getRole())) {
                messages.add(AiMessage.from(record.getContent()));
            }
        }
        if (messages.size() > maxMessages) {
            return messages.subList(messages.size() - maxMessages, messages.size());
        }
        return messages;
    }

    // ---- Converters ----

    private ConversationDto toDto(Conversation c) {
        return ConversationDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .cities(fromJson(c.getCitiesJson()))
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private MessageDto toMsgDto(ConversationMessage m) {
        List<SourceReference> sources = Collections.emptyList();
        if (m.getSourcesJson() != null) {
            try {
                sources = objectMapper.readValue(m.getSourcesJson(),
                        new TypeReference<List<SourceReference>>() {});
            } catch (Exception ignored) {}
        }
        return MessageDto.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .sources(sources)
                .timestamp(m.getTimestamp())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
