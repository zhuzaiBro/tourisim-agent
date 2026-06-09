package com.tourism.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * TourismRAG 多城市旅游智能助手
 *
 * 技术架构：
 * - LLM: 阿里通义千问 (Qwen) via DashScope API
 * - 向量库: Milvus（语义检索）
 * - 结构化库: MySQL 8（城市/景点元数据）
 * - RAG 框架: LangChain4j 0.36+
 */
@SpringBootApplication
@EnableAsync  // 支持异步 Embedding 写入，加速数据摄入
public class TourismRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(TourismRagApplication.class, args);
    }
}
