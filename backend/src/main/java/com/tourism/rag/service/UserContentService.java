package com.tourism.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.ItineraryResponse;
import com.tourism.rag.entity.UserFavorite;
import com.tourism.rag.entity.UserNote;
import com.tourism.rag.entity.UserPlanBookEntry;
import com.tourism.rag.repository.UserFavoriteRepository;
import com.tourism.rag.repository.UserNoteRepository;
import com.tourism.rag.repository.UserPlanBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserContentService {

    private final UserFavoriteRepository favoriteRepository;
    private final UserNoteRepository noteRepository;
    private final UserPlanBookRepository planBookRepository;
    private final ItineraryAgentService itineraryAgentService;
    private final ObjectMapper objectMapper;

    // ---- Favorites ----

    public List<Map<String, Object>> listFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toFavoriteDto)
                .toList();
    }

    @Transactional
    public Map<String, Object> addFavorite(Long userId, Map<String, Object> body) {
        UserFavorite fav = UserFavorite.builder()
                .userId(userId)
                .content((String) body.get("content"))
                .citiesJson(toJson(body.get("cities")))
                .question((String) body.get("question"))
                .sessionTitle((String) body.getOrDefault("sessionTitle", ""))
                .build();
        return toFavoriteDto(favoriteRepository.save(fav));
    }

    @Transactional
    public void deleteFavorite(Long userId, Long id) {
        favoriteRepository.findByIdAndUserId(id, userId)
                .ifPresent(f -> favoriteRepository.delete(f));
    }

    // ---- Notes ----

    public List<Map<String, Object>> listNotes(Long userId) {
        return noteRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toNoteDto)
                .toList();
    }

    @Transactional
    public Map<String, Object> createNote(Long userId, Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        UserNote note = UserNote.builder()
                .id(id)
                .userId(userId)
                .title((String) body.getOrDefault("title", "新笔记"))
                .content((String) body.getOrDefault("content", ""))
                .tagsJson(toJson(body.getOrDefault("tags", List.of())))
                .pinned(Boolean.TRUE.equals(body.get("pinned")))
                .sourceMessage((String) body.get("sourceMessage"))
                .build();
        return toNoteDto(noteRepository.save(note));
    }

    @Transactional
    public Map<String, Object> updateNote(Long userId, String id, Map<String, Object> body) {
        UserNote note = noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("笔记不存在"));
        if (body.containsKey("title")) note.setTitle((String) body.get("title"));
        if (body.containsKey("content")) note.setContent((String) body.get("content"));
        if (body.containsKey("tags")) note.setTagsJson(toJson(body.get("tags")));
        if (body.containsKey("pinned")) note.setPinned(Boolean.TRUE.equals(body.get("pinned")));
        return toNoteDto(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(Long userId, String id) {
        noteRepository.findByIdAndUserId(id, userId)
                .ifPresent(n -> noteRepository.delete(n));
    }

    // ---- Plan book ----

    public List<Map<String, Object>> listPlanBook(Long userId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserPlanBookEntry entry : planBookRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            itineraryAgentService.getById(entry.getItineraryId()).ifPresent(itinerary -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", entry.getItineraryId());
                dto.put("customTitle", entry.getCustomTitle());
                dto.put("savedAt", entry.getCreatedAt().toString());
                dto.put("cityName", itinerary.getCityName());
                dto.put("cityCode", itinerary.getCityCode());
                dto.put("startDate", itinerary.getStartDate());
                dto.put("endDate", itinerary.getEndDate());
                dto.put("totalDays", itinerary.getTotalDays());
                dto.put("tripSummary", itinerary.getTripSummary());
                dto.put("itinerary", itinerary);
                items.add(dto);
            });
        }
        return items;
    }

    @Transactional
    public Map<String, Object> savePlanBook(Long userId, String itineraryId, String customTitle) {
        if (planBookRepository.findByUserIdAndItineraryId(userId, itineraryId).isPresent()) {
            throw new IllegalStateException("已在规划册中");
        }
        ItineraryResponse itinerary = itineraryAgentService.getById(itineraryId)
                .orElseThrow(() -> new IllegalArgumentException("行程不存在"));
        UserPlanBookEntry entry = UserPlanBookEntry.builder()
                .userId(userId)
                .itineraryId(itineraryId)
                .customTitle(customTitle)
                .build();
        planBookRepository.save(entry);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", itineraryId);
        dto.put("customTitle", customTitle);
        dto.put("savedAt", entry.getCreatedAt().toString());
        dto.put("itinerary", itinerary);
        return dto;
    }

    @Transactional
    public void removePlanBook(Long userId, String itineraryId) {
        planBookRepository.deleteByUserIdAndItineraryId(userId, itineraryId);
    }

    public boolean isPlanBookSaved(Long userId, String itineraryId) {
        return planBookRepository.findByUserIdAndItineraryId(userId, itineraryId).isPresent();
    }

    private Map<String, Object> toFavoriteDto(UserFavorite f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("content", f.getContent());
        m.put("cities", fromJsonList(f.getCitiesJson()));
        m.put("question", f.getQuestion());
        m.put("sessionTitle", f.getSessionTitle());
        m.put("savedAt", f.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toNoteDto(UserNote n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        m.put("tags", fromJsonList(n.getTagsJson()));
        m.put("pinned", Boolean.TRUE.equals(n.getPinned()));
        m.put("sourceMessage", n.getSourceMessage());
        m.put("createdAt", n.getCreatedAt().toString());
        m.put("updatedAt", n.getUpdatedAt().toString());
        m.put("wordCount", n.getContent() != null ? n.getContent().length() : 0);
        return m;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
