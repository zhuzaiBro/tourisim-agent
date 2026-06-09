package com.tourism.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "conversation_messages", indexes = {
    @Index(name = "idx_msg_conv_id", columnList = "conversation_id")
})
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    /** "user" or "assistant" */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** JSON-serialized list of SourceReference objects (nullable for user messages) */
    @Column(name = "sources_json", columnDefinition = "TEXT")
    private String sourcesJson;

    private LocalDateTime timestamp;

    @PrePersist
    void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}
