package com.tourism.rag.dto;

import com.tourism.rag.dto.ChatResponse.SourceReference;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationDto {
    private String id;
    private String title;
    private List<String> cities;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class MessageDto {
        private Long id;
        private String role;
        private String content;
        private List<SourceReference> sources;
        private LocalDateTime timestamp;
    }
}
