package com.tourism.rag.controller;

import com.tourism.rag.dto.auth.AdminSetupRequest;
import com.tourism.rag.dto.auth.AuthResponse;
import com.tourism.rag.dto.auth.LoginRequest;
import com.tourism.rag.dto.auth.RegisterRequest;
import com.tourism.rag.security.AuthUser;
import com.tourism.rag.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 普通用户注册 */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    /** 普通用户登录 */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    /** 管理员专用登录（非 ADMIN 角色返回 400） */
    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.adminLogin(req));
    }

    /**
     * 首次管理员账号初始化（需要 setupKey）
     * 只有当系统中尚无 ADMIN 账号时才能调用
     */
    @PostMapping("/admin/setup")
    public ResponseEntity<AuthResponse> setupAdmin(@Valid @RequestBody AdminSetupRequest req) {
        return ResponseEntity.ok(authService.setupAdmin(req));
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole()
        ));
    }
}
