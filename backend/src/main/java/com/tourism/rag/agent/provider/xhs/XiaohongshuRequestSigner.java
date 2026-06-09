package com.tourism.rag.agent.provider.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 为小红书 Web API 生成 x-s / x-t 等签名头。
 * 优先 HTTP 签名服务，失败则回退到本地 Python 脚本（xhshow）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaohongshuRequestSigner {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.xhs.sign-url:}")
    private String signUrl;

    @Value("${agent.xhs.sign-script:scripts/xhs_sign_once.py}")
    private String signScript;

    @Value("${agent.xhs.python:python3}")
    private String pythonBin;

    public Map<String, String> signPost(String uri, String cookie, Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uri", uri);
        body.put("method", "POST");
        body.put("cookie", cookie);
        body.put("payload", payload);

        if (signUrl != null && !signUrl.isBlank()) {
            Map<String, String> viaHttp = signViaHttp(body);
            if (!viaHttp.isEmpty()) {
                return viaHttp;
            }
            log.warn("[XHS] HTTP 签名服务不可用，尝试本地 Python 脚本");
        }

        return signViaPython(body);
    }

    private Map<String, String> signViaHttp(Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = restTemplate.exchange(
                    signUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return parseSignHeaders(resp.getBody());
        } catch (Exception e) {
            log.warn("[XHS] 签名服务失败 {}: {}", signUrl, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> signViaPython(Map<String, Object> body) {
        File script = new File(signScript);
        if (!script.isFile()) {
            log.warn("[XHS] 签名脚本不存在: {}，请 pip install xhshow 并确认脚本路径", script.getAbsolutePath());
            return Map.of();
        }
        try {
            String input = objectMapper.writeValueAsString(body);
            ProcessBuilder pb = new ProcessBuilder(pythonBin, script.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (OutputStream os = proc.getOutputStream()) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
            }

            String output;
            try (InputStream is = proc.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = proc.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                log.warn("[XHS] Python 签名超时");
                return Map.of();
            }
            if (proc.exitValue() != 0) {
                log.warn("[XHS] Python 签名失败 exit={} output={}", proc.exitValue(), truncate(output));
                return Map.of();
            }

            Map<String, String> headers = parseSignHeaders(output);
            if (headers.isEmpty()) {
                log.warn("[XHS] Python 签名无有效头: {}", truncate(output));
            } else {
                log.info("[XHS] Python 签名成功");
            }
            return headers;
        } catch (Exception e) {
            log.warn("[XHS] Python 签名异常: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> parseSignHeaders(String body) throws Exception {
        if (body == null || body.isBlank()) return Map.of();
        JsonNode root = objectMapper.readTree(body);
        if (root.has("error")) {
            log.warn("[XHS] 签名返回错误: {}", root.path("error").asText());
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : new String[]{"x-s", "x-t", "x-s-common", "x-b3-traceid", "x-xray-traceid"}) {
            String v = root.path(key).asText(root.path("headers").path(key).asText(""));
            if (!v.isBlank()) {
                result.put(key, v);
            }
        }
        return result;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    public static String newSearchId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 21);
    }
}
