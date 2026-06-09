package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 高德 Web 服务统一 HTTP 客户端（URI 编码、响应解析）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GaodeApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.map.gaode.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public JsonNode get(String baseUrl, Map<String, ?> queryParams) {
        if (!isConfigured()) {
            return null;
        }
        try {
            Map<String, Object> params = new LinkedHashMap<>(queryParams);
            params.put("key", apiKey);
            params.put("output", params.getOrDefault("output", "json"));

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
            params.forEach(builder::queryParam);

            URI uri = builder.encode(StandardCharsets.UTF_8).build().toUri();
            log.debug("[GaodeApi] GET {}", uri);

            String body = restTemplate.getForObject(uri, String.class);
            if (body == null || body.isBlank()) {
                log.warn("[GaodeApi] 空响应: {}", baseUrl);
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("[GaodeApi] 非成功 status={} info={} url={}",
                        root.path("status").asText(), root.path("info").asText(), baseUrl);
            }
            return root;
        } catch (Exception e) {
            log.warn("[GaodeApi] 请求失败 {}: {}", baseUrl, e.getMessage());
            return null;
        }
    }

    public boolean isSuccess(JsonNode root) {
        return root != null && "1".equals(root.path("status").asText());
    }
}
