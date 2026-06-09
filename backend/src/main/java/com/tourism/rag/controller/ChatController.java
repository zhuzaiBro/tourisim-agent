package com.tourism.rag.controller;

import com.tourism.rag.dto.ChatRequest;
import com.tourism.rag.dto.ChatResponse;
import com.tourism.rag.security.AuthUser;
import com.tourism.rag.service.TourismChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final TourismChatService tourismChatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal AuthUser user) {
        Long userId = user != null ? user.getId() : null;
        log.info("收到聊天请求: cities={}, userId={}", request.getCities(), userId);
        return ResponseEntity.ok(tourismChatService.chat(request, userId));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal AuthUser user) {
        Long userId = user != null ? user.getId() : null;
        log.info("收到流式聊天请求: cities={}, userId={}", request.getCities(), userId);

        SseEmitter emitter = new SseEmitter(120_000L);

        tourismChatService.chatStream(request, userId).subscribe(
            token -> {
                try {
                    // 转义换行，保持与前端 replace(/\\n/g, '\n') 对称
                    String escaped = token.replace("\n", "\\n");
                    emitter.send(escaped, MediaType.TEXT_PLAIN);
                } catch (IOException e) {
                    log.warn("SSE 写入失败，客户端可能已断开: {}", e.getMessage());
                    emitter.completeWithError(e);
                }
            },
            error -> {
                log.error("流式响应错误", error);
                try {
                    emitter.send("[ERROR] 服务暂时不可用，请稍后重试", MediaType.TEXT_PLAIN);
                } catch (IOException ignored) {}
                emitter.complete();
            },
            emitter::complete
        );

        return emitter;
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        tourismChatService.clearSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
