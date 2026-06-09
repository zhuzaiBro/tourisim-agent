package com.tourism.rag.controller;

import com.tourism.rag.dto.ConversationDto;
import com.tourism.rag.dto.ConversationDto.MessageDto;
import com.tourism.rag.security.AuthUser;
import com.tourism.rag.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话历史管理接口
 *
 * GET    /api/conversations                  用户对话列表
 * POST   /api/conversations                  创建对话
 * PUT    /api/conversations/{id}/title       修改标题
 * DELETE /api/conversations/{id}             删除对话
 * GET    /api/conversations/{id}/messages    获取消息列表
 * DELETE /api/conversations/{id}/messages    清空消息
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<List<ConversationDto>> list(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(conversationService.getUserConversations(user.getId()));
    }

    @PostMapping
    public ResponseEntity<ConversationDto> create(
            @AuthenticationPrincipal AuthUser user,
            @RequestBody Map<String, Object> body) {
        String title = (String) body.getOrDefault("title", "新对话");
        @SuppressWarnings("unchecked")
        List<String> cities = (List<String>) body.getOrDefault("cities", List.of());
        return ResponseEntity.ok(conversationService.createConversation(user.getId(), title, cities));
    }

    @PutMapping("/{id}/title")
    public ResponseEntity<ConversationDto> updateTitle(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUser user,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        return ResponseEntity.ok(conversationService.updateTitle(id, user.getId(), title));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUser user) {
        conversationService.deleteConversation(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageDto>> messages(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(conversationService.getMessages(id, user.getId()));
    }

    @DeleteMapping("/{id}/messages")
    public ResponseEntity<Void> clearMessages(
            @PathVariable String id,
            @AuthenticationPrincipal AuthUser user) {
        conversationService.clearMessages(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
