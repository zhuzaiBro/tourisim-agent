package com.tourism.rag.agent.provider.xhs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 小红书笔记搜索客户端。
 * <p>
 * 推荐模式 {@code cookie}：浏览器复制 Cookie + 可选本地签名服务。
 */
@Slf4j
@Component
public class XiaohongshuApiClient {

    private static final String WEB_SEARCH_URI = "/api/sns/web/v1/search/notes";
    private static final String WEB_SEARCH_URL = "https://edith.xiaohongshu.com" + WEB_SEARCH_URI;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final XiaohongshuRequestSigner signer;

    @Value("${agent.xhs.enabled:false}")
    private boolean enabled;

    @Value("${agent.xhs.mode:cookie}")
    private String mode;

    @Value("${agent.xhs.cookie:}")
    private String cookie;

    @Value("${agent.xhs.api-key:}")
    private String apiKey;

    @Value("${agent.xhs.client-id:}")
    private String clientId;

    @Value("${agent.xhs.client-secret:}")
    private String clientSecret;

    @Value("${agent.xhs.base-url:https://open.xiaohongshu.com}")
    private String baseUrl;

    @Value("${agent.xhs.token-path:/oauth2/access_token}")
    private String tokenPath;

    @Value("${agent.xhs.search-path:/api/sns/v1/search/notes}")
    private String searchPath;

    @Value("${agent.xhs.gateway-base-url:https://api.justoneapi.com}")
    private String gatewayBaseUrl;

    @Value("${agent.xhs.gateway-search-path:/api/xiaohongshu/search-note/v3}")
    private String gatewaySearchPath;

    private String accessToken;
    private long tokenExpireAtMs;

    public XiaohongshuApiClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                                 XiaohongshuRequestSigner signer) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.signer = signer;
    }

    public boolean isConfigured() {
        if (!enabled) return false;
        return switch (mode.toLowerCase()) {
            case "cookie" -> cookie != null && !cookie.isBlank();
            case "gateway" -> apiKey != null && !apiKey.isBlank();
            case "official" -> clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
            default -> false;
        };
    }

    public List<XiaohongshuNote> searchNotes(String keyword, int page, int pageSize) {
        if (!isConfigured()) {
            log.debug("[XHS] 未配置，跳过搜索 keyword={}", keyword);
            return List.of();
        }
        try {
            return switch (mode.toLowerCase()) {
                case "cookie" -> searchViaCookie(keyword, page, pageSize);
                case "gateway" -> searchViaGateway(keyword, page);
                case "official" -> searchViaOfficial(keyword, page, pageSize);
                default -> List.of();
            };
        } catch (Exception e) {
            log.warn("[XHS] 搜索失败 keyword={}: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private List<XiaohongshuNote> searchViaCookie(String keyword, int page, int pageSize) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", keyword);
        payload.put("page", page);
        payload.put("page_size", pageSize);
        payload.put("search_id", XiaohongshuRequestSigner.newSearchId());
        payload.put("sort", "general");
        payload.put("note_type", 0);

        HttpHeaders headers = buildWebHeaders();
        Map<String, String> signed = signer.signPost(WEB_SEARCH_URI, cookie, payload);
        signed.forEach(headers::set);

        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("序列化搜索参数失败", e);
        }

        log.info("[XHS][cookie] POST keyword={} signed={}", keyword, !signed.isEmpty());
        ResponseEntity<String> resp = restTemplate.exchange(
                WEB_SEARCH_URL,
                HttpMethod.POST,
                new HttpEntity<>(bodyBytes, headers),
                String.class);

        List<XiaohongshuNote> notes = parseNotesFromJson(resp.getBody());
        if (notes.isEmpty()) {
            log.warn("[XHS][cookie] 无结果或鉴权失败。请确认: 1) Cookie 未过期 "
                    + "2) pip install xhshow 已安装 3) 签名成功 signed=true");
        }
        return notes;
    }

    private HttpHeaders buildWebHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("accept", "application/json, text/plain, */*");
        headers.set("origin", "https://www.xiaohongshu.com");
        headers.set("referer", "https://www.xiaohongshu.com/");
        headers.set("user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("cookie", cookie.trim());
        return headers;
    }

    private List<XiaohongshuNote> searchViaGateway(String keyword, int page) {
        URI uri = UriComponentsBuilder.fromHttpUrl(gatewayBaseUrl + gatewaySearchPath)
                .queryParam("token", apiKey)
                .queryParam("keyword", keyword)
                .queryParam("page", page)
                .queryParam("sort", "popularity_descending")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        log.info("[XHS][gateway] GET keyword={}", keyword);
        ResponseEntity<String> resp = restTemplate.getForEntity(uri, String.class);
        return parseNotesFromJson(resp.getBody());
    }

    private List<XiaohongshuNote> searchViaOfficial(String keyword, int page, int pageSize) {
        ensureAccessToken();

        String url = baseUrl + searchPath;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyword", keyword);
        body.put("page", page);
        body.put("page_size", pageSize);
        body.put("sort", "general");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        log.info("[XHS][official] POST keyword={}", keyword);
        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return parseNotesFromJson(resp.getBody());
    }

    private void ensureAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAtMs - 60_000) {
            return;
        }
        String url = baseUrl + tokenPath;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "client_credentials");

        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(form, headers), String.class);

        try {
            JsonNode root = objectMapper.readTree(resp.getBody());
            accessToken = root.path("access_token").asText(
                    root.path("data").path("access_token").asText(""));
            int expiresIn = root.path("expires_in").asInt(
                    root.path("data").path("expires_in").asInt(7200));
            tokenExpireAtMs = System.currentTimeMillis() + expiresIn * 1000L;
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("未获取到 access_token");
            }
            log.info("[XHS] OAuth token 已刷新，有效期 {}s", expiresIn);
        } catch (Exception e) {
            throw new IllegalStateException("小红书 OAuth 失败: " + e.getMessage(), e);
        }
    }

    List<XiaohongshuNote> parseNotesFromJson(String body) {
        if (body == null || body.isBlank()) return List.of();
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("[XHS] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }

        if (!root.path("success").asBoolean(true) && root.has("success")) {
            log.warn("[XHS] success=false msg={}", root.path("msg").asText());
        }

        String code = root.path("code").asText("0");
        if (root.has("code") && !code.isBlank()
                && !"0".equals(code) && !"200".equals(code) && !"success".equalsIgnoreCase(code)) {
            log.warn("[XHS] 业务码 code={} message={}", code,
                    root.path("message").asText(root.path("msg").asText()));
            return List.of();
        }

        JsonNode data = root.path("data");
        List<JsonNode> candidates = collectNoteNodes(data);
        if (candidates.isEmpty()) {
            candidates = collectNoteNodes(root);
        }

        List<XiaohongshuNote> notes = new ArrayList<>();
        for (JsonNode node : candidates) {
            XiaohongshuNote note = toNote(node);
            if (note != null && note.getTitle() != null && !note.getTitle().isBlank()) {
                notes.add(note);
            }
        }
        return notes;
    }

    private List<JsonNode> collectNoteNodes(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) return result;

        if (node.isArray()) {
            node.forEach(n -> result.addAll(collectNoteNodes(n)));
            return result;
        }

        if (node.has("note_card")) {
            result.add(node);
            return result;
        }

        if (looksLikeNote(node)) {
            result.add(node);
            return result;
        }

        for (String field : List.of("items", "notes", "note_list", "list", "records", "noteList")) {
            if (node.has(field)) {
                result.addAll(collectNoteNodes(node.get(field)));
            }
        }
        return result;
    }

    private boolean looksLikeNote(JsonNode node) {
        return node.has("title") || node.has("display_title")
                || node.has("note_card") || node.has("note_id") || node.has("id");
    }

    private XiaohongshuNote toNote(JsonNode wrapper) {
        JsonNode node = wrapper;
        JsonNode card = wrapper.path("note_card");
        if (!card.isMissingNode()) {
            node = card;
        }

        String title = firstText(node, "title", "display_title", "note_title");
        String desc = firstText(node, "desc", "description", "content", "abstract");
        String id = firstText(node, "note_id", "id", "noteId");
        if (id.isBlank()) {
            id = firstText(wrapper, "id", "note_id");
        }

        String author = firstText(node.path("user"), "nickname", "name", "user_name");
        if (author.isBlank()) {
            author = firstText(node, "author", "nickname");
        }

        JsonNode interact = node.path("interact_info");
        int likes = parseCount(interact.path("liked_count"), node.path("liked_count"), node.path("likes"));
        int collects = parseCount(interact.path("collected_count"), node.path("collected_count"), node.path("collects"));
        String url = firstText(node, "url", "share_url", "link");

        if (title.isBlank()) return null;

        return XiaohongshuNote.builder()
                .noteId(id)
                .title(title.trim())
                .description(desc)
                .author(author)
                .likes(likes)
                .collects(collects)
                .url(url)
                .build();
    }

    private static int parseCount(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n == null || n.isMissingNode()) continue;
            if (n.isNumber()) return n.asInt();
            String s = n.asText("").replaceAll("[^\\d]", "");
            if (!s.isBlank()) {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText("").trim();
            if (!v.isBlank()) return v;
        }
        return "";
    }
}
