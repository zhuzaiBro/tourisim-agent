package com.tourism.rag.service;

import com.tourism.rag.dto.auth.AdminSetupRequest;
import com.tourism.rag.dto.auth.AuthResponse;
import com.tourism.rag.dto.auth.LoginRequest;
import com.tourism.rag.dto.auth.RegisterRequest;
import com.tourism.rag.entity.User;
import com.tourism.rag.repository.UserRepository;
import com.tourism.rag.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.admin.setup-key:}")
    private String adminSetupKey;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("USER")
                .build();
        user = userRepository.save(user);
        log.info("新用户注册: {}", user.getEmail());
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("邮箱或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        return toAuthResponse(user);
    }

    /** Admin-only login — same credentials check but rejects non-admin users */
    public AuthResponse adminLogin(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("邮箱或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("该账号没有管理员权限");
        }
        return toAuthResponse(user);
    }

    /**
     * First-run admin account creation.
     * Only succeeds if (1) setup key matches and (2) no admin exists yet.
     */
    public AuthResponse setupAdmin(AdminSetupRequest req) {
        if (adminSetupKey.isBlank() || !adminSetupKey.equals(req.getSetupKey())) {
            throw new IllegalArgumentException("初始化密钥无效");
        }
        if (userRepository.existsByRole("ADMIN")) {
            throw new IllegalArgumentException("管理员账号已存在，请直接登录");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("该邮箱已被注册");
        }
        User admin = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("ADMIN")
                .build();
        admin = userRepository.save(admin);
        log.info("管理员账号初始化: {}", admin.getEmail());
        return toAuthResponse(admin);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
