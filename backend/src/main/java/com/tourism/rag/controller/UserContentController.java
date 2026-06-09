package com.tourism.rag.controller;

import com.tourism.rag.security.AuthUser;
import com.tourism.rag.service.UserContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserContentController {

    private final UserContentService userContentService;

    // ---- Favorites ----

    @GetMapping("/favorites")
    public ResponseEntity<?> listFavorites(@AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ResponseEntity.ok(userContentService.listFavorites(user.getId()));
    }

    @PostMapping("/favorites")
    public ResponseEntity<?> addFavorite(@AuthenticationPrincipal AuthUser user,
                                          @RequestBody Map<String, Object> body) {
        requireUser(user);
        return ResponseEntity.ok(userContentService.addFavorite(user.getId(), body));
    }

    @DeleteMapping("/favorites/{id}")
    public ResponseEntity<Void> deleteFavorite(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable Long id) {
        requireUser(user);
        userContentService.deleteFavorite(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- Notes ----

    @GetMapping("/notes")
    public ResponseEntity<?> listNotes(@AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ResponseEntity.ok(userContentService.listNotes(user.getId()));
    }

    @PostMapping("/notes")
    public ResponseEntity<?> createNote(@AuthenticationPrincipal AuthUser user,
                                         @RequestBody Map<String, Object> body) {
        requireUser(user);
        return ResponseEntity.ok(userContentService.createNote(user.getId(), body));
    }

    @PutMapping("/notes/{id}")
    public ResponseEntity<?> updateNote(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable String id,
                                         @RequestBody Map<String, Object> body) {
        requireUser(user);
        return ResponseEntity.ok(userContentService.updateNote(user.getId(), id, body));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<Void> deleteNote(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable String id) {
        requireUser(user);
        userContentService.deleteNote(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- Plan book ----

    @GetMapping("/plan-books")
    public ResponseEntity<?> listPlanBooks(@AuthenticationPrincipal AuthUser user) {
        requireUser(user);
        return ResponseEntity.ok(userContentService.listPlanBook(user.getId()));
    }

    @PostMapping("/plan-books")
    public ResponseEntity<?> savePlanBook(@AuthenticationPrincipal AuthUser user,
                                           @RequestBody Map<String, String> body) {
        requireUser(user);
        String itineraryId = body.get("itineraryId");
        String customTitle = body.get("customTitle");
        return ResponseEntity.ok(userContentService.savePlanBook(user.getId(), itineraryId, customTitle));
    }

    @DeleteMapping("/plan-books/{itineraryId}")
    public ResponseEntity<Void> removePlanBook(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable String itineraryId) {
        requireUser(user);
        userContentService.removePlanBook(user.getId(), itineraryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/plan-books/{itineraryId}/exists")
    public ResponseEntity<Map<String, Boolean>> planBookExists(@AuthenticationPrincipal AuthUser user,
                                                              @PathVariable String itineraryId) {
        requireUser(user);
        return ResponseEntity.ok(Map.of(
                "saved", userContentService.isPlanBookSaved(user.getId(), itineraryId)));
    }

    private void requireUser(AuthUser user) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("需要登录");
        }
    }
}
