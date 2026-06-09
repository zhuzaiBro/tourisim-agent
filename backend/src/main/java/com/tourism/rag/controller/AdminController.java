package com.tourism.rag.controller;

import com.tourism.rag.dto.auth.AuthResponse;
import com.tourism.rag.entity.City;
import com.tourism.rag.entity.User;
import com.tourism.rag.repository.ConversationMessageRepository;
import com.tourism.rag.repository.ConversationRepository;
import com.tourism.rag.repository.UserRepository;
import com.tourism.rag.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 管理后台 API
 * GET  /api/admin/stats           数据概览统计
 * GET  /api/admin/stats/daily     最近7天消息量
 * GET  /api/admin/users           用户列表
 * GET  /api/admin/cities          城市列表（含未启用）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final CityRepository cityRepository;

    /** 数据大盘汇总 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime weekStart = todayStart.minusDays(7);

        long totalUsers = userRepository.count();
        long totalConversations = conversationRepository.count();
        long totalMessages = messageRepository.count();
        long todayConversations = conversationRepository.countByCreatedAtAfter(todayStart);
        long todayMessages = messageRepository.countByTimestampAfter(todayStart);
        long weekMessages = messageRepository.countByTimestampAfter(weekStart);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalConversations", totalConversations);
        result.put("totalMessages", totalMessages);
        result.put("todayConversations", todayConversations);
        result.put("todayMessages", todayMessages);
        result.put("weekMessages", weekMessages);
        return ResponseEntity.ok(result);
    }

    /** 最近7天每日消息量（用于折线图） */
    @GetMapping("/stats/daily")
    public ResponseEntity<List<Map<String, Object>>> dailyStats() {
        LocalDateTime since = LocalDateTime.now().toLocalDate().minusDays(6).atStartOfDay();
        List<Object[]> rows = messageRepository.countByDaySince(since);

        // Build a map of date -> count for all 7 days
        Map<String, Long> dayMap = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            String key = LocalDateTime.now().minusDays(i).format(fmt);
            dayMap.put(key, 0L);
        }
        for (Object[] row : rows) {
            String date = row[0].toString().substring(5); // MM-dd from yyyy-MM-dd
            dayMap.put(date, ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        dayMap.forEach((date, count) -> result.add(Map.of("date", date, "count", count)));
        return ResponseEntity.ok(result);
    }

    /** 用户列表（最近注册，分页） */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pageResult = userRepository.findAll(pageable);

        List<Map<String, Object>> items = pageResult.getContent().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "items", items,
                "total", pageResult.getTotalElements(),
                "pages", pageResult.getTotalPages()
        ));
    }

    /** 城市列表（含未启用） */
    @GetMapping("/cities")
    public ResponseEntity<List<City>> cities() {
        return ResponseEntity.ok(cityRepository.findAll());
    }

    /** 切换城市启用状态 */
    @PatchMapping("/cities/{code}/toggle")
    public ResponseEntity<City> toggleCity(@PathVariable String code) {
        return cityRepository.findByCode(code).map(city -> {
            city.setEnabled(!Boolean.TRUE.equals(city.getEnabled()));
            return ResponseEntity.ok(cityRepository.save(city));
        }).orElse(ResponseEntity.notFound().build());
    }
}
