package com.tourism.rag.agent;

/**
 * 外部数据（高德/RAG）不可用且已禁用 Mock 兜底时抛出。
 */
public class AgentDataUnavailableException extends RuntimeException {

    public AgentDataUnavailableException(String message) {
        super(message);
    }

    public AgentDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
